package javax.xml.namespace;

/**
 * 最小桩：仅用于在 --limit-modules java.base 下编译 javax.xml.stream 桩。
 * 运行期由 Android 平台（android.jar）提供真实实现，本桩只进编译期 classpath
 * （compileOnly），不打入 APK。
 */
public interface NamespaceContext {
    String getNamespaceURI(String prefix);

    String getPrefix(String namespaceURI);

    java.util.Iterator<String> getPrefixes(String namespaceURI);
}
