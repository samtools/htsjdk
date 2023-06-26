package htsjdk.beta.io.bundle;

import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.Log;
import htsjdk.utils.ValidationUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

//TODO: Once the schema is finalized, we need to bump the version # to 1.0, and publish it.

/**
 * Methods for serializing and deserializing Bundles to and from JSON strings.
 */
public class BundleJSON {
    public static final String BUNDLE_EXTENSION = ".json";
    private static final Log LOG = Log.getInstance(BundleJSON.class);

    public static final String JSON_PROPERTY_SCHEMA_NAME      = "schemaName";
    public static final String JSON_PROPERTY_SCHEMA_VERSION   = "schemaVersion";
    public static final String JSON_PROPERTY_PRIMARY          = "primary";
    public static final String JSON_PROPERTY_PATH             = "path";
    public static final String JSON_PROPERTY_FORMAT           = "format";
    public static final String JSON_SCHEMA_NAME               = "htsbundle";
    public static final String JSON_SCHEMA_VERSION            = "0.1.0"; // TODO: bump this to 1.0.0

    final private static Set<String> TOP_LEVEL_PROPERTIES = Collections.unmodifiableSet(
            new HashSet<String>() {
                private static final long serialVersionUID = 1L;
                {
                    add(JSON_PROPERTY_SCHEMA_NAME);
                    add(JSON_PROPERTY_SCHEMA_VERSION);
                    add(JSON_PROPERTY_PRIMARY);
                }});

    /**
     * Serialize this bundle to a JSON string representation. All resources in the bundle must
     * be {@link IOPathResource}s for serialization to succeed. Stream resources cannot be serialized.
     *
     * @param bundle the {@link Bundle} to serialize to JSON
     * @return a JSON string representation of this bundle
     * @throws IllegalArgumentException if any resource in bundle is not an IOPathResources.
     */
    public static String toJSON(final Bundle bundle) {
        final JSONObject outerJSON = new JSONObject();
        outerJSON.put(JSON_PROPERTY_SCHEMA_NAME, JSON_SCHEMA_NAME);
        outerJSON.put(JSON_PROPERTY_SCHEMA_VERSION, JSON_SCHEMA_VERSION);
        outerJSON.put(JSON_PROPERTY_PRIMARY, bundle.getPrimaryContentType());

        bundle.forEach(bundleResource -> {
            final Optional<IOPath> resourcePath = bundleResource.getIOPath();
            if (!resourcePath.isPresent()) {
                throw new IllegalArgumentException("Bundle resource requires a valid path to be serialized");
            }

            // generate JSON for each bundle resource
            final JSONObject resourceJSON = new JSONObject().put(JSON_PROPERTY_PATH, resourcePath.get().getURIString());
            if (bundleResource.getFileFormat().isPresent()) {
                resourceJSON.put(JSON_PROPERTY_FORMAT, bundleResource.getFileFormat().get());
            }
            outerJSON.put(bundleResource.getContentType(), resourceJSON);
        });

        return prettyPrintJSON(outerJSON);
    }

    /**
     * Create a Bundle from jsonString.
     *
     * @param jsonString a valid JSON string conforming to the bundle schema
     * @return a {@link Bundle} created from jsonString
     */
    public static Bundle toBundle(final String jsonString) {
        return toBundle(ValidationUtils.nonEmpty(jsonString, "resource list"), HtsPath::new);
    }

    /**
     * Create a Bundle from jsonString using a custom class that implements {@link IOPath} for all resources.
     *
     * @param jsonString a valid JSON string conforming to the bundle schema
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type <T>
     * @param <T> the IOPath-derived type to use for IOPathResources
     * @return  a newly created {@link Bundle}
     */
    public static <T extends IOPath> Bundle toBundle(
            final String jsonString,
            final Function<String, T> ioPathConstructor) {
        ValidationUtils.nonEmpty(jsonString, "JSON string");
        ValidationUtils.nonNull(ioPathConstructor, "IOPath-derived class constructor");

        final List<BundleResource> resources  = new ArrayList<>();
        String primaryContentType;

        try {
            final JSONObject jsonDocument = new JSONObject(jsonString);
            if (jsonDocument == null || jsonString.length() < 1) {
                throw new IllegalArgumentException(
                        String.format("JSON file parsing failed %s", jsonString));
            }

            // validate the schema name
            final String schemaName = getPropertyAsString(JSON_PROPERTY_SCHEMA_NAME, jsonDocument);
            if (!schemaName.equals(JSON_SCHEMA_NAME)) {
                throw new IllegalArgumentException(
                        String.format("Expected bundle schema name %s but found %s", JSON_SCHEMA_NAME, schemaName));
            }

            // validate the schema version
            final String schemaVersion = getPropertyAsString(JSON_PROPERTY_SCHEMA_VERSION, jsonDocument);
            if (!schemaVersion.equals(JSON_SCHEMA_VERSION)) {
                throw new IllegalArgumentException(String.format("Expected bundle schema version %s but found %s",
                        JSON_SCHEMA_VERSION, schemaVersion));
            }
            primaryContentType = getPropertyAsString(JSON_PROPERTY_PRIMARY, jsonDocument);

            jsonDocument.toMap().forEach((String contentType, Object jsonDocObj) -> {
                if (! (jsonDocObj instanceof JSONObject jsonDoc)) {
                    throw new IllegalStateException("Not a JSONObject");
                }

                if (!TOP_LEVEL_PROPERTIES.contains(contentType)) {
                    final String format = jsonDoc.optString(JSON_PROPERTY_FORMAT, null);
                    final IOPathResource ioPathResource = new IOPathResource(
                            ioPathConstructor.apply(getPropertyAsString(JSON_PROPERTY_PATH, jsonDoc)),
                            contentType,
                            format == null ?
                                    null :
                                    getPropertyAsString(JSON_PROPERTY_FORMAT, jsonDoc));
                    resources.add(ioPathResource);
                }
            });
            if (resources.isEmpty()) {
                LOG.warn("Empty resource bundle found: ", jsonString);
            }
        } catch (JSONException | UnsupportedOperationException e) {
            throw new IllegalArgumentException(e);
        }

        return new Bundle(primaryContentType, resources);
    }

    // Simple pretty-printer to produce indented JSON strings from a Json document. Note that
    // this is not generalized and will only work on Json documents produced by BundleJSON::toJSON.
    private static String prettyPrintJSON(final JSONObject jsonDocument) {
        final StringBuilder sb = new StringBuilder();
        final String TOP_LEVEL_PROPERTY_FORMAT = "  \"%s\":\"%s\"";

        try {
            sb.append("{\n");

            // schema name
            final String schemaName = getPropertyAsString(JSON_PROPERTY_SCHEMA_NAME, jsonDocument);
            sb.append(String.format(TOP_LEVEL_PROPERTY_FORMAT, JSON_PROPERTY_SCHEMA_NAME, schemaName));
            sb.append(",\n");

            // schema version
            final String schemaVersion = getPropertyAsString(JSON_PROPERTY_SCHEMA_VERSION, jsonDocument);
            sb.append(String.format(TOP_LEVEL_PROPERTY_FORMAT, JSON_PROPERTY_SCHEMA_VERSION, schemaVersion));
            sb.append(",\n");

            // primary
            final String primary = getPropertyAsString(JSON_PROPERTY_PRIMARY, jsonDocument);
            sb.append(String.format(TOP_LEVEL_PROPERTY_FORMAT, JSON_PROPERTY_PRIMARY, primary));
            sb.append(",\n");

            final List<String> formattedResources = new ArrayList<>();
            jsonDocument.toMap().forEach((String contentType, Object jsonDocObj) -> {
                if (! (jsonDocObj instanceof JSONObject jsonDoc)) {
                    throw new IllegalStateException("Not a JSONObject");
                }

                if (!TOP_LEVEL_PROPERTIES.contains(contentType)) {
                    final JSONObject format = jsonDoc.getJSONObject(JSON_PROPERTY_FORMAT);
                    final StringBuilder resSB = new StringBuilder();
                    if (format != null) {
                        resSB.append(String.format("{\"%s\":\"%s\",\"%s\":\"%s\"}",
                                JSON_PROPERTY_PATH,
                                getPropertyAsString(JSON_PROPERTY_PATH, jsonDoc),
                                JSON_PROPERTY_FORMAT,
                                getPropertyAsString(JSON_PROPERTY_FORMAT, jsonDoc)));
                    } else {
                        resSB.append(String.format("{\"%s\":\"%s\"}",
                                JSON_PROPERTY_PATH,
                                getPropertyAsString(JSON_PROPERTY_PATH, jsonDoc)));
                    }
                    formattedResources.add(String.format("  \"%s\":%s", contentType, resSB.toString()));
                }
            });
            sb.append(formattedResources.stream().collect(Collectors.joining(",\n", "", "\n")));
            sb.append("}\n");
        } catch (JSONException | UnsupportedOperationException e) {
            throw new IllegalArgumentException(e);
        }

        return sb.toString();
    }

    // return the value of propertyName from jsonDocument as a String value
    private static String getPropertyAsString(final String propertyName, final JSONObject jsonDocument) {
        return jsonDocument.getString(propertyName);
//        final JSONObject propertyValue = jsonDocument.at(propertyName);
//        if (propertyValue == null) {
//            throw new IllegalArgumentException(
//                    String.format("JSON bundle is missing the required property %s (%s)",
//                            propertyName,
//                            jsonDocument.toString()));
//        } else if (!propertyValue.isString()) {
//            throw new IllegalArgumentException(
//                    String.format("Expected string value for bundle property %s but found %s",
//                            propertyName,
//                            propertyValue.toString()));
//        }
//        return propertyValue.asString();
    }

}
