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
        final JSONObject outerJSON = new JSONObject()
            .put(JSON_PROPERTY_SCHEMA_NAME, JSON_SCHEMA_NAME)
            .put(JSON_PROPERTY_SCHEMA_VERSION, JSON_SCHEMA_VERSION)
            .put(JSON_PROPERTY_PRIMARY, bundle.getPrimaryContentType());

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

        return outerJSON.toString(1);
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
            if (jsonString.length() < 1) {
                throw new IllegalArgumentException(
                        String.format("JSON file parsing failed %s", jsonString));
            }

            // validate the schema name
            final String schemaName = getRequiredPropertyAsString(jsonDocument, JSON_PROPERTY_SCHEMA_NAME);
            if (!schemaName.equals(JSON_SCHEMA_NAME)) {
                throw new IllegalArgumentException(
                        String.format("Expected bundle schema name %s but found %s", JSON_SCHEMA_NAME, schemaName));
            }

            // validate the schema version
            final String schemaVersion = getRequiredPropertyAsString(jsonDocument, JSON_PROPERTY_SCHEMA_VERSION);
            if (!schemaVersion.equals(JSON_SCHEMA_VERSION)) {
                throw new IllegalArgumentException(String.format("Expected bundle schema version %s but found %s",
                        JSON_SCHEMA_VERSION, schemaVersion));
            }
            primaryContentType = getRequiredPropertyAsString(jsonDocument, JSON_PROPERTY_PRIMARY);

            jsonDocument.keySet().forEach((String contentType) -> {
                if (! (jsonDocument.get(contentType) instanceof JSONObject jsonDoc)) {
                    return;
                }

                if (!TOP_LEVEL_PROPERTIES.contains(contentType)) {
                    final String format = jsonDoc.optString(JSON_PROPERTY_FORMAT, null);
                    final IOPathResource ioPathResource = new IOPathResource(
                            ioPathConstructor.apply(getRequiredPropertyAsString(jsonDoc, JSON_PROPERTY_PATH)),
                            contentType,
                            format == null ?
                                    null :
                                    jsonDoc.optString(JSON_PROPERTY_FORMAT, null));
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

    private static String getRequiredPropertyAsString(JSONObject jsonDocument, String propertyName) {
        final String propertyValue = jsonDocument.optString(propertyName, null);
        if (propertyValue == null) {
            throw new IllegalArgumentException(
                    String.format("JSON bundle is missing the required property %s (%s)",
                            propertyName,
                            jsonDocument));
        }

        return propertyValue;
    }
}
