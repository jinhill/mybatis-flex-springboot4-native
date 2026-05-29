package com.jinhill.mybatisflex.config;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.query.CPI;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import com.mybatisflex.core.table.TableInfo;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.cache.decorators.FifoCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.SoftCache;
import org.apache.ibatis.cache.decorators.WeakCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.BaseStatementHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl;
import org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MyBatis-Flex GraalVM Native Image 适配配置。
 *
 * <p>解决 Spring Boot 4 AOT 编译时三个核心问题：
 * <ol>
 *   <li>{@code MapperFactoryBean} 构造器参数由字符串改为 Class 对象，修复
 *       {@code No qualifying bean of type 'java.lang.Class<?>'}。</li>
 *   <li>显式注入 {@code sqlSessionFactory}，修复
 *       {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。</li>
 *   <li>为所有 Mapper 接口及关联类型注册反射/代理 Hint，确保 native 运行时可访问。</li>
 * </ol>
 *
 * <p>同时需在 {@code META-INF/spring/aot.factories} 中注册 AOT 处理器：
 * <pre>{@code
 * org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor=\
 *   com.jinhill.mybatisflex.config.MyBatisFlexNativeConfig$MyBatisFlexBeanFactoryInitializationAotProcessor
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(MyBatisFlexNativeConfig.MyBatisFlexRuntimeHintsRegistrar.class)
public class MyBatisFlexNativeConfig {

    /** 一次性注册所有成员类别，用于需要完整反射访问的类。 */
    private static final MemberCategory[] ALL_MEMBERS = MemberCategory.values();

    /** 仅字段可访问，用于内部实现类（如 BoundSql）。 */
    private static final MemberCategory[] FIELDS_ONLY = {
            MemberCategory.ACCESS_DECLARED_FIELDS
    };

    @Bean
    MyBatisFlexBeanFactoryInitializationAotProcessor myBatisFlexBeanFactoryInitializationAotProcessor() {
        return new MyBatisFlexBeanFactoryInitializationAotProcessor();
    }

    /**
     * 声明为 static，确保此 BeanPostProcessor 在外部 @Configuration 类实例化之前就已就绪，
     * 避免 Spring 因过早初始化而产生警告。
     */
    @Bean
    static MyBatisFlexMapperFactoryBeanPostProcessor myBatisFlexMapperFactoryBeanPostProcessor() {
        return new MyBatisFlexMapperFactoryBeanPostProcessor();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RuntimeHints
    // ─────────────────────────────────────────────────────────────────────────

    static class MyBatisFlexRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

            // ① 全量反射：MyBatis 核心 + MyBatis-Spring + MyBatis-Flex 核心
            //    SqlSessionFactory / SqlSessionTemplate / SqlSessionDaoSupport 也在此统一注册，
            //    确保 native 运行时 setSqlSessionFactory / setSqlSessionTemplate 可被反射调用。
            Stream.of(
                    // MyBatis scripting & logging
                    RawLanguageDriver.class,
                    XMLLanguageDriver.class,
                    Slf4jImpl.class,
                    Log.class,
                    JakartaCommonsLoggingImpl.class,
                    Log4j2Impl.class,
                    Jdk14LoggingImpl.class,
                    StdOutImpl.class,
                    NoLoggingImpl.class,
                    // MyBatis cache
                    PerpetualCache.class,
                    FifoCache.class,
                    LruCache.class,
                    SoftCache.class,
                    WeakCache.class,
                    // MyBatis-Spring session & mapper
                    SqlSessionFactory.class,
                    SqlSessionTemplate.class,   // ← 统一在此注册
                    SqlSessionFactoryBean.class,
                    SqlSessionDaoSupport.class, // ← 统一在此注册，保证 setter 可反射调用
                    MapperFactoryBean.class,
                    // MyBatis-Flex 核心
                    BaseMapper.class,
                    QueryWrapper.class,
                    Row.class,
                    TableInfo.class,
                    FlexConfiguration.class,
                    FlexSqlSessionFactoryBuilder.class,
                    CPI.class,
                    // 常用集合（MyBatis 结果集映射需要）
                    ArrayList.class,
                    HashMap.class,
                    TreeSet.class,
                    HashSet.class
            ).forEach(type -> hints.reflection().registerType(type, ALL_MEMBERS));

            // ② 字段访问：MyBatis 内部实现类（拦截器通过反射读取字段）
            Stream.of(
                    BoundSql.class,
                    RoutingStatementHandler.class,
                    BaseStatementHandler.class
            ).forEach(type -> hints.reflection().registerType(type, FIELDS_ONLY));

            // ③ FlexMapperFactoryBean（可选，不存在时静默跳过）
            tryRegisterType("com.mybatisflex.spring.FlexMapperFactoryBean",
                    classLoader, hints, ALL_MEMBERS);

            // ④ MyBatis XML schema 资源
            Stream.of(
                    "org/apache/ibatis/builder/xml/*.dtd",
                    "org/apache/ibatis/builder/xml/*.xsd"
            ).forEach(hints.resources()::registerPattern);

            // ⑤ MyBatis 拦截器 JDK 动态代理
            hints.proxies().registerJdkProxy(StatementHandler.class);
            hints.proxies().registerJdkProxy(Executor.class);
            hints.proxies().registerJdkProxy(ResultSetHandler.class);
            hints.proxies().registerJdkProxy(ParameterHandler.class);
        }

        /** 安全注册：类不存在时静默跳过，避免硬编码可选依赖导致编译失败。 */
        private void tryRegisterType(String className, ClassLoader classLoader,
                                     RuntimeHints hints, MemberCategory... categories) {
            try {
                hints.reflection().registerType(
                        ClassUtils.forName(className, classLoader), categories);
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AOT 处理器：扫描所有 MapperFactoryBean，注册 Mapper 相关 Hint
    // ─────────────────────────────────────────────────────────────────────────

    static class MyBatisFlexBeanFactoryInitializationAotProcessor
            implements BeanFactoryInitializationAotProcessor, BeanRegistrationExcludeFilter {

        /**
         * 排除 MapperScannerConfigurer 的 AOT 代码生成。
         * MapperScannerConfigurer 在运行时仍会执行（它是 BeanDefinitionRegistryPostProcessor），
         * 排除仅针对 AOT 阶段，防止二次扫描与已生成代码冲突。
         */
        @Override
        public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
            return MapperScannerConfigurer.class.equals(registeredBean.getBeanClass());
        }

        @Override
        public BeanFactoryInitializationAotContribution processAheadOfTime(
                ConfigurableListableBeanFactory beanFactory) {

            String[] beanNames = beanFactory.getBeanNamesForType(MapperFactoryBean.class);
            if (beanNames.length == 0) {
                return null;
            }

            return (context, code) -> {
                RuntimeHints hints = context.getRuntimeHints();
                for (String beanName : beanNames) {
                    // FactoryBean 的 beanName 带 "&" 前缀，取真实名
                    String realName = beanName.startsWith("&") ? beanName.substring(1) : beanName;
                    if (!beanFactory.containsBeanDefinition(realName)) {
                        continue;
                    }
                    BeanDefinition bd = beanFactory.getBeanDefinition(realName);
                    Class<?> mapperType = resolveMapperInterface(bd, beanFactory.getBeanClassLoader());
                    if (mapperType == null) {
                        continue;
                    }
                    registerMapperHints(mapperType, hints, beanFactory.getBeanClassLoader());
                }
            };
        }

        /** 从 BeanDefinition 的 propertyValues 中解析 mapperInterface，兼容 Class 和 String 两种存储形式。 */
        private Class<?> resolveMapperInterface(BeanDefinition bd, ClassLoader classLoader) {
            PropertyValue pv = bd.getPropertyValues().getPropertyValue("mapperInterface");
            if (pv == null || pv.getValue() == null) {
                return null;
            }
            Object val = pv.getValue();
            if (val instanceof Class<?> cls) {
                return cls;
            }
            if (val instanceof String name) {
                try {
                    return ClassUtils.forName(name, classLoader);
                } catch (ClassNotFoundException ignored) {
                }
            }
            return null;
        }

        /** 为单个 Mapper 接口注册完整的反射、代理、资源及方法关联类型 Hint。 */
        private void registerMapperHints(Class<?> mapperType, RuntimeHints hints, ClassLoader classLoader) {
            // Mapper 接口自身
            hints.reflection().registerType(mapperType, ALL_MEMBERS);
            hints.proxies().registerJdkProxy(mapperType);
            // 对应 XML mapper 文件（如果存在）
            hints.resources().registerPattern(
                    mapperType.getName().replace('.', '/').concat(".xml"));
            // Mapper 方法涉及的返回值、参数、SqlProvider 类型
            for (Method method : ReflectionUtils.getAllDeclaredMethods(mapperType)) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                ReflectionUtils.makeAccessible(method);
                registerSqlProviderTypes(method, hints, SelectProvider.class,
                        SelectProvider::value, SelectProvider::type);
                registerSqlProviderTypes(method, hints, InsertProvider.class,
                        InsertProvider::value, InsertProvider::type);
                registerSqlProviderTypes(method, hints, UpdateProvider.class,
                        UpdateProvider::value, UpdateProvider::type);
                registerSqlProviderTypes(method, hints, DeleteProvider.class,
                        DeleteProvider::value, DeleteProvider::type);
                registerReflectionTypeIfNecessary(
                        MyBatisMapperTypeUtils.resolveReturnClass(mapperType, method), hints);
                MyBatisMapperTypeUtils.resolveParameterClasses(mapperType, method)
                        .forEach(t -> registerReflectionTypeIfNecessary(t, hints));
            }
        }

        @SafeVarargs
        private <T extends Annotation> void registerSqlProviderTypes(
                Method method, RuntimeHints hints, Class<T> annotationType,
                Function<T, Class<?>>... resolvers) {
            for (T ann : method.getAnnotationsByType(annotationType)) {
                for (Function<T, Class<?>> resolver : resolvers) {
                    registerReflectionTypeIfNecessary(resolver.apply(ann), hints);
                }
            }
        }

        /** 跳过 null、基本类型及 java.* 标准库类型，仅注册业务类型。 */
        private void registerReflectionTypeIfNecessary(Class<?> type, RuntimeHints hints) {
            if (type != null && !type.isPrimitive() && !type.getName().startsWith("java")) {
                hints.reflection().registerType(type, ALL_MEMBERS);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BeanDefinition 修正器：在 AOT 代码生成前修正 MapperFactoryBean 的定义
    // ─────────────────────────────────────────────────────────────────────────

    static class MyBatisFlexMapperFactoryBeanPostProcessor
            implements MergedBeanDefinitionPostProcessor, BeanFactoryAware {

        private static final String MAPPER_FACTORY_BEAN = "org.mybatis.spring.mapper.MapperFactoryBean";

        private ConfigurableBeanFactory beanFactory;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = (ConfigurableBeanFactory) beanFactory;
        }

        @Override
        public void postProcessMergedBeanDefinition(
                RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
            if (ClassUtils.isPresent(MAPPER_FACTORY_BEAN, this.beanFactory.getBeanClassLoader())) {
                fixMapperFactoryBeanDefinition(beanDefinition);
            }
        }

        private void fixMapperFactoryBeanDefinition(RootBeanDefinition bd) {
            if (!bd.hasBeanClass()
                    || !MapperFactoryBean.class.isAssignableFrom(bd.getBeanClass())) {
                return;
            }

            // ① 显式注入 sqlSessionFactory（优先）或 sqlSessionTemplate。
            //    AOT 不支持 AUTOWIRE_BY_TYPE，必须在此写入硬引用，Spring AOT
            //    才能在生成的代码中正确调用 setSqlSessionFactory()。
            if (!bd.getPropertyValues().contains("sqlSessionFactory")
                    && !bd.getPropertyValues().contains("sqlSessionTemplate")) {
                bd.getPropertyValues().add("sqlSessionFactory",
                        new RuntimeBeanReference("sqlSessionFactory"));
            }

            // ② 修正构造器参数：将字符串类名替换为真实 Class 对象。
            //    ClassPathMapperScanner 把 Mapper 类名以字符串写入 constructorArguments，
            //    Spring AOT 无法将其解析为 Class<?> bean，导致启动失败。
            if (bd.getResolvableType().hasUnresolvableGenerics()) {
                Class<?> mapperInterface = resolveMapperInterface(bd);
                if (mapperInterface != null) {
                    ConstructorArgumentValues args = new ConstructorArgumentValues();
                    args.addGenericArgumentValue(mapperInterface);
                    bd.setConstructorArgumentValues(args);
                    bd.setTargetType(ResolvableType.forClassWithGenerics(
                            bd.getBeanClass(), mapperInterface));
                }
            }
        }

        private Class<?> resolveMapperInterface(RootBeanDefinition bd) {
            try {
                Object value = bd.getPropertyValues().get("mapperInterface");
                if (value instanceof Class<?> cls) {
                    return cls;
                }
                if (value instanceof String name) {
                    return ClassUtils.forName(name, this.beanFactory.getBeanClassLoader());
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 类型工具：解析 Mapper 方法的返回值与参数类型（含泛型）
    // ─────────────────────────────────────────────────────────────────────────

    static class MyBatisMapperTypeUtils {

        private MyBatisMapperTypeUtils() {}

        static Class<?> resolveReturnClass(Class<?> mapperInterface, Method method) {
            Type resolved = TypeParameterResolver.resolveReturnType(method, mapperInterface);
            return typeToClass(resolved, method.getReturnType());
        }

        static Set<Class<?>> resolveParameterClasses(Class<?> mapperInterface, Method method) {
            return Stream.of(TypeParameterResolver.resolveParamTypes(method, mapperInterface))
                    .map(t -> typeToClass(t, t instanceof Class<?> c ? c : Object.class))
                    .collect(Collectors.toSet());
        }

        private static Class<?> typeToClass(Type src, Class<?> fallback) {
            if (src instanceof Class<?> cls) {
                return cls.isArray() ? cls.getComponentType() : cls;
            }
            if (src instanceof ParameterizedType pt) {
                // Map 类型取 value 泛型，其余取第一个泛型参数
                int index = (pt.getRawType() instanceof Class<?> raw
                        && Map.class.isAssignableFrom(raw)
                        && pt.getActualTypeArguments().length > 1) ? 1 : 0;
                return typeToClass(pt.getActualTypeArguments()[index], fallback);
            }
            return fallback;
        }
    }
}
