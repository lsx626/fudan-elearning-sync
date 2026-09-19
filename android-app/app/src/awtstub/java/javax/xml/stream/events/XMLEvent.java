package javax.xml.stream.events;
public interface XMLEvent extends javax.xml.stream.XMLStreamConstants {
    int getEventType();
    javax.xml.stream.Location getLocation();
    boolean isStartElement();
    boolean isEndElement();
    boolean isEntityReference();
    boolean isProcessingInstruction();
    boolean isCharacters();
    boolean isStartDocument();
    boolean isEndDocument();
    StartElement asStartElement();
    EndElement asEndElement();
    Characters asCharacters();
    java.lang.String getSystemId();
}