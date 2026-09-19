package javax.xml.namespace;

import java.util.Objects;

/**
 * 最小桩：仅用于在 --limit-modules java.base 下编译 javax.xml.stream 桩。
 * 运行期由 Android 平台（android.jar）提供真实实现，本桩只进编译期 classpath
 * （compileOnly），不打入 APK。
 */
public class QName {
    private final String namespaceURI;
    private final String localPart;
    private final String prefix;

    public QName(String localPart) {
        this("", localPart, "");
    }

    public QName(String namespaceURI, String localPart) {
        this(namespaceURI, localPart, "");
    }

    public QName(String namespaceURI, String localPart, String prefix) {
        this.namespaceURI = (namespaceURI == null) ? "" : namespaceURI;
        this.localPart = localPart;
        this.prefix = (prefix == null) ? "" : prefix;
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }

    public String getLocalPart() {
        return localPart;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public String toString() {
        if (namespaceURI.isEmpty()) {
            return localPart;
        }
        return "{" + namespaceURI + "}" + localPart;
    }

    @Override
    public int hashCode() {
        return namespaceURI.hashCode() ^ localPart.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QName)) {
            return false;
        }
        QName q = (QName) o;
        return namespaceURI.equals(q.namespaceURI) && localPart.equals(q.localPart);
    }
}
