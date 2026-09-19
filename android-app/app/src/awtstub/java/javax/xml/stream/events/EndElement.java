package javax.xml.stream.events;
public interface EndElement extends XMLEvent {
    javax.xml.namespace.QName getName();
    java.util.Iterator getNamespaces();
}