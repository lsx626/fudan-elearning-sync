package javax.xml.stream;
public abstract class XMLOutputFactory {
    public static XMLOutputFactory newFactory() { return null; }
    public static XMLOutputFactory newInstance() { return null; }
    public static XMLOutputFactory newInstance(String factoryId, ClassLoader classLoader) { return null; }
    public abstract XMLStreamWriter createXMLStreamWriter(java.io.OutputStream stream) throws XMLStreamException;
    public abstract XMLStreamWriter createXMLStreamWriter(java.io.Writer writer) throws XMLStreamException;
    public abstract void setProperty(String name, Object value) throws IllegalArgumentException;
    public abstract Object getProperty(String name) throws IllegalArgumentException;
    public static final String IS_REPAIRING_NAMESPACES = "javax.xml.stream.isRepairingNamespaces";
}