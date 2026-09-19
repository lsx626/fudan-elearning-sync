package javax.xml.catalog;
/**
 * 桩：xmlbeans 仅把 CatalogResolver 当作类型引用，不调用其方法。
 * 方法签名故意不引用 org.xml.sax（属 java.xml 模块，桩编译期不可见）。
 */
public interface CatalogResolver {
}