package htsjdk.beta.io.bundle;

import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.Log;
import htsjdk.utils.ValidationUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
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

    final private static Set<String> TOP_LEVEL_PROPERTIES =
            Set.of(JSON_PROPERTY_SCHEMA_NAME, JSON_PROPERTY_SCHEMA_VERSION, JSON_PROPERTY_PRIMARY);

    /**
     * Serialize a bundle to a JSON string representation. All resources in the bundle must
     * be {@link IOPathResource}s for serialization to succeed. Stream resources cannot be serialized.
     *
     * @param bundle the {@link Bundle} to serialize to JSON
     * @return a JSON string representation of this bundle
     * @throws IllegalArgumentException if any resource in bundle is not an IOPathResources.
     */
    public static String toJSON(final Bundle bundle) {
        ValidationUtils.validateArg(
                !bundle.getPrimaryContentType().equals(JSON_PROPERTY_PRIMARY),
                "Primary content type cannot be named 'primary'");
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
     * Convert a (non-empty) List of Bundles to a JSON array string representation.
     * @param bundles a List of Bundles to serialize to JSON
     * @return a JSON string (array) representation of the list of bundles
     * @throw IllegalArgumentException if the list is empty
     */
    public static String toJSON(final List<Bundle> bundles) {
        if (bundles.isEmpty()) {
            throw new IllegalArgumentException("A bundle list must contain at least one bundle");
        }
        return bundles.stream()
                .map(BundleJSON::toJSON)
                .collect(Collectors.joining(",\n", "[", "]"));
    }

     /**
      * Create a Bundle from a jsonString.
      *
      * @param jsonString a valid JSON string conforming to the bundle schema (for compatibility, a bundle list is also
      *                   accepted, as long as it only contains a single bundle)
      * @return a {@link Bundle} created from jsonString
      */
     public static Bundle toBundle(final String jsonString) {
        return toBundle(ValidationUtils.nonEmpty(jsonString, "resource list"), HtsPath::new);
     }

    /**
     * Create a Bundle from jsonString using a custom class that implements {@link IOPath} for all resources.
     * (For compatibility, a bundle list string is also accepted, as long as it only contains a single bundle).
     *
     * @param jsonString a valid JSON string conforming to the bundle schema
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type <T>
     * @param <T> the IOPath-derived type to use for IOPathResources
     * @return a newly created {@link Bundle}
     */
    public static <T extends IOPath> Bundle toBundle(
            final String jsonString,
            final Function<String, T> ioPathConstructor) {
        ValidationUtils.nonEmpty(jsonString, "JSON string");
        ValidationUtils.nonNull(ioPathConstructor, "IOPath-derived class constructor");
        try {
            return toBundle(new JSONObject(jsonString), ioPathConstructor);
        } catch (JSONException | UnsupportedOperationException e) {
            // see if the user provided a collection instead of a single bundle, and if so, present it as
            // a Bundle as long as it only contains one BundleResource
            try {
                final List<Bundle> bundles = toBundleList(jsonString, ioPathConstructor);
                if (bundles.size() > 1) {
                    throw new IllegalArgumentException(
                        String.format("A JSON string with more than one bundle was provided but only a single bundle is allowed in this context (%s)",
                                e.getMessage()));
                }
                return bundles.stream().findFirst().get();
            } catch (JSONException | UnsupportedOperationException e2) {
                throw new IllegalArgumentException(
                        String.format("The JSON can be interpreted neither as an individual bundle (%s) nor as a bundle collection (%s)",
                                e.getMessage(),
                                e2.getMessage()),
                        e);
            }
        }
    }

    /**
     * Create a List<Bundle> from a jsonString, using a custom class that implements {@link IOPath} for all
     * resources.
     * @param jsonString the json string must conform to the bundle schema, and may contain an array or single object
     * @param ioPathConstructor constructor to use to create the backing IOPath for all resources
     * @return List<Bundle>
     * @param <T> IOPath-derived class to use for IOPathResources
     */
    public static <T extends IOPath> List<Bundle> toBundleList(
            final String jsonString,
            final Function<String, T> ioPathConstructor) {
        ValidationUtils.nonEmpty(jsonString, "json bundle string");
        ValidationUtils.nonNull(ioPathConstructor, "IOPath-derived class constructor");

        final List<Bundle> bundles = new ArrayList<>();
        try {
            final JSONArray jsonArray = new JSONArray(jsonString);
            jsonArray.forEach(element -> {
                if (! (element instanceof JSONObject jsonObject)) {
                    throw new IllegalArgumentException(
                            String.format("Bundle collections may contain only Bundle objects, found %s",
                                    element.toString()));
                }
                bundles.add(toBundle(jsonObject, ioPathConstructor));
            });
        } catch (JSONException | UnsupportedOperationException e) {
            // see if the user provided a single bundle instead of a collection, if so, wrap it up as a collection
            try {
                bundles.add(toBundle(new JSONObject(jsonString), ioPathConstructor));
            } catch (JSONException | UnsupportedOperationException e2) {
                throw new IllegalArgumentException(
                        String.format("JSON can be interpreted neither as an individual bundle (%s) nor as a bundle collection (%s)",
                        e2.getMessage(),
                        e.getMessage()),
                        e);
            }
        }
        if (bundles.isEmpty()) {
            throw new IllegalArgumentException("JSON bundle collection must contain at least one bundle");
        }
        return bundles;
    }

    /**
     * Create a List<Bundle> from a jsonString.
     *
     * @param jsonString a JSON strings that conform to the bundle schema; may be an array or single object
     * @return a {@link List<Bundle>} created from a Collection of jsonStrings
     */
    public static List<Bundle> toBundleList(final String jsonString) {
        return toBundleList(jsonString, HtsPath::new);
    }

    private static <T extends IOPath> Bundle toBundle(
            final JSONObject jsonObject, // must be a single Bundle object
            final Function<String, T> ioPathConstructor) {
        try {
            // validate the schema name
            final String schemaName = getRequiredPropertyAsString(jsonObject, JSON_PROPERTY_SCHEMA_NAME);
            if (!schemaName.equals(JSON_SCHEMA_NAME)) {
                throw new IllegalArgumentException(
                        String.format("Expected bundle schema name %s but found %s", JSON_SCHEMA_NAME, schemaName));
            }

            // validate the schema version
            final String schemaVersion = getRequiredPropertyAsString(jsonObject, JSON_PROPERTY_SCHEMA_VERSION);
            if (!schemaVersion.equals(JSON_SCHEMA_VERSION)) {
                throw new IllegalArgumentException(String.format("Expected bundle schema version %s but found %s",
                        JSON_SCHEMA_VERSION, schemaVersion));
            }

            final String primaryContentType = getRequiredPropertyAsString(jsonObject, JSON_PROPERTY_PRIMARY);
            final Collection<BundleResource> bundleResources = toBundleResources(jsonObject, ioPathConstructor);
            return new Bundle(primaryContentType, bundleResources);
        } catch (JSONException | UnsupportedOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }
    private static <T extends IOPath> IOPathResource toBundleResource(
            final String contentType,
            final JSONObject jsonObject,
            final Function<String, T> ioPathConstructor) {
        final String format = jsonObject.optString(JSON_PROPERTY_FORMAT, null);
        return new IOPathResource(
                ioPathConstructor.apply(getRequiredPropertyAsString(jsonObject, JSON_PROPERTY_PATH)),
                contentType,
                format);
    }
    private static <T extends IOPath> Collection<BundleResource> toBundleResources(
            final JSONObject jsonResources,
            final Function<String, T> ioPathConstructor) {

        final List<BundleResource> bundleResources = new ArrayList<>(); // default capacity of 10 seems right
        jsonResources.keySet().forEach(key -> {
            if (!TOP_LEVEL_PROPERTIES.contains(key)) {
                if (jsonResources.get(key) instanceof JSONObject resourceObject) {
                    bundleResources.add(toBundleResource(key, resourceObject, ioPathConstructor));
                } else {
                    throw new IllegalArgumentException(
                            String.format("Bundle resources may contain only BundleResource objects, found %s", key));
                }
            }
        });
        return bundleResources;
    }

    private static String getRequiredPropertyAsString(final JSONObject jsonDocument, final String propertyName) {
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
