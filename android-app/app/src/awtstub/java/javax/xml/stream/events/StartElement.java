package javax.xml.stream.events;
public interface StartElement extends XMLEvent {
    javax.xml.namespace.QName getName();
    java.util.Iterator getAttributes();
    java.util.Iterator getNamespaces();
    javax.xml.namespace.NamespaceContext getNamespaceContext();
}