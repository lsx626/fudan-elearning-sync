package javax.xml.stream;
public abstract class XMLInputFactory {
    public static XMLInputFactory newFactory() { return null; }
    public static XMLInputFactory newInstance() { return null; }
    public static XMLInputFactory newInstance(String factoryId, ClassLoader classLoader) { return null; }
    public abstract XMLStreamReader createXMLStreamReader(java.io.InputStream stream) throws XMLStreamException;
    public abstract XMLStreamReader createXMLStreamReader(String systemId, java.io.InputStream stream) throws XMLStreamException;
    public abstract XMLStreamReader createXMLStreamReader(java.io.Reader reader) throws XMLStreamException;
    public abstract void setProperty(String name, Object value) throws IllegalArgumentException;
    public abstract Object getProperty(String name) throws IllegalArgumentException;
    public static final String IS_NAMESPACE_AWARE = "javax.xml.stream.isNamespaceAware";
    public static final String IS_COALESCING = "javax.xml.stream.isCoalescing";
    public static final String IS_REPLACING_ENTITIES = "javax.xml.stream.isReplacingEntities";
    public static final String IS_SUPPORTING_EXTERNAL_ENTITIES = "javax.xml.stream.isSupportingExternalEntities";
    public static final String SUPPORT_DTD = "javax.xml.stream.supportDTD";
    public static final String REPORTER = "javax.xml.stream.reporter";
    public static final String RESOLVER = "javax.xml.stream.resolver";
    public static final String ALLOCATOR = "javax.xml.stream.allocator";
}