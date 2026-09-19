package javax.xml.stream;
public class XMLStreamException extends Exception {
    private final Location location;
    public XMLStreamException() { super(); this.location = null; }
    public XMLStreamException(String msg) { super(msg); this.location = null; }
    public XMLStreamException(String msg, Throwable th) { super(msg, th); this.location = null; }
    public XMLStreamException(String msg, Location location, Throwable th) { super(msg, th); this.location = location; }
    public XMLStreamException(String msg, Location location) { super(msg); this.location = location; }
    public Location getLocation() { return location; }
}