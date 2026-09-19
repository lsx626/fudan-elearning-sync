package javax.xml.catalog;
public abstract class CatalogFeatures {
    public static final String PREFER_PUBLIC = "public";
    public static final String PREFER_SYSTEM = "system";
    public static Builder builder() { return new Builder(); }
    public static CatalogFeatures defaults() { return null; }
    public abstract String get(Feature feature);
    public static final class Builder {
        public Builder with(Feature feature, String value) { return this; }
        public CatalogFeatures build() { return null; }
    }
    public enum Feature {
        PREFER,
        RESOLVE,
        CATALOG_CACHE,
        CATALOG_LOAD
    }
}