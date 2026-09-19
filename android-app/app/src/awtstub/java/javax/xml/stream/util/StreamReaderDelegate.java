package javax.xml.stream.util;
public class StreamReaderDelegate implements javax.xml.stream.XMLStreamReader {
    private javax.xml.stream.XMLStreamReader parent;
    public StreamReaderDelegate() {}
    public StreamReaderDelegate(javax.xml.stream.XMLStreamReader reader) { this.parent = reader; }
    public void setParent(javax.xml.stream.XMLStreamReader reader) { this.parent = reader; }
    public javax.xml.stream.XMLStreamReader getParent() { return parent; }
    public Object getProperty(String name) { return null; }
    public int next() { return 0; }
    public void close() {}
    public String getNamespaceURI(String prefix) { return null; }
    public boolean isStartElement() { return false; }
    public boolean isEndElement() { return false; }
    public boolean isCharacters() { return false; }
    public boolean isWhiteSpace() { return false; }
    public String getAttributeValue(String namespaceURI, String localName) { return null; }
    public int getAttributeCount() { return 0; }
    public javax.xml.namespace.QName getAttributeName(int index) { return null; }
    public String getAttributeNamespace(int index) { return null; }
    public String getAttributeLocalName(int index) { return null; }
    public String getAttributePrefix(int index) { return null; }
    public String getAttributeType(int index) { return null; }
    public String getAttributeValue(int index) { return null; }
    public boolean isAttributeSpecified(int index) { return false; }
    public int getNamespaceCount() { return 0; }
    public String getNamespacePrefix(int index) { return null; }
    public String getNamespaceURI(int index) { return null; }
    public javax.xml.namespace.NamespaceContext getNamespaceContext() { return null; }
    public int getEventType() { return 0; }
    public String getText() { return null; }
    public char[] getTextCharacters() { return null; }
    public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) { return 0; }
    public int getTextStart() { return 0; }
    public int getTextLength() { return 0; }
    public String getEncoding() { return null; }
    public boolean hasText() { return false; }
    public javax.xml.namespace.QName getName() { return null; }
    public String getLocalName() { return null; }
    public boolean hasName() { return false; }
    public String getNamespaceURI() { return null; }
    public String getPrefix() { return null; }
    public String getVersion() { return null; }
    public boolean isStandalone() { return false; }
    public boolean standaloneSet() { return false; }
    public String getCharacterEncodingScheme() { return null; }
    public String getPITarget() { return null; }
    public String getPIData() { return null; }
    public javax.xml.stream.Location getLocation() { return null; }
    public String getElementText() { return null; }
    public int nextTag() { return 0; }
    public void require(int type, String namespaceURI, String localName) {}
    public boolean hasNext() { return false; }
}