package javax.xml.stream;
public abstract class XMLEventFactory {
    public static XMLEventFactory newFactory() { return null; }
    public static XMLEventFactory newInstance() { return null; }
    public static XMLEventFactory newInstance(String factoryId, ClassLoader classLoader) { return null; }
    public void setLocation(Location location) {}
    public javax.xml.stream.events.XMLEvent createXMLEvent() { return null; }
}