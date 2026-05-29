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
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference; // 引入 RuntimeBeanReference
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.support.RegisteredBean;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(MyBatisFlexNativeConfig.MyBatisFlexRuntimeHintsRegistrar.class)
public class MyBatisFlexNativeConfig {

    private static final MemberCategory[] ALL_MEMBERS = {
            MemberCategory.ACCESS_PUBLIC_FIELDS,
            MemberCategory.ACCESS_DECLARED_FIELDS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
    };

    @Bean
    MyBatisFlexBeanFactoryInitializationAotProcessor myBatisFlexBeanFactoryInitializationAotProcessor() {
        return new MyBatisFlexBeanFactoryInitializationAotProcessor();
    }

    @Bean
    static MyBatisFlexMapperFactoryBeanPostProcessor myBatisFlexMapperFactoryBeanPostProcessor() {
        return new MyBatisFlexMapperFactoryBeanPostProcessor();
    }

    static class MyBatisFlexRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // 注册 MyBatis 及核心类库的反射 Hint
            Stream.of(
                    RawLanguageDriver.class,
                    XMLLanguageDriver.class,
                    SqlSessionFactory.class,
                    SqlSessionFactoryBean.class,
                    PerpetualCache.class,
                    FifoCache.class,
                    LruCache.class,
                    SoftCache.class,
                    WeakCache.class,
                    Slf4jImpl.class,
                    Log.class,
                    JakartaCommonsLoggingImpl.class,
                    Log4j2Impl.class,
                    Jdk14LoggingImpl.class,
                    StdOutImpl.class,
                    NoLoggingImpl.class,
                    ArrayList.class,
                    HashMap.class,
                    TreeSet.class,
                    HashSet.class,
                    MapperFactoryBean.class,       // 显式注册 MapperFactoryBean 的反射
                    SqlSessionDaoSupport.class     // 显式注册父类反射，确保 setSqlSessionFactory 方法能被调用
            ).forEach(x -> hints.reflection().registerType(x, ALL_MEMBERS));

            // 如果项目使用了 MyBatis-Flex 特有的 FlexMapperFactoryBean，也对其注册反射
            try {
                Class<?> flexMapperFactoryBean = ClassUtils.forName("com.mybatisflex.spring.FlexMapperFactoryBean", classLoader);
                hints.reflection().registerType(flexMapperFactoryBean, ALL_MEMBERS);
            } catch (ClassNotFoundException e) {
                // ignore
            }

            // 注册 MyBatis-Flex 核心类
            Stream.of(
                    BaseMapper.class,
                    QueryWrapper.class,
                    Row.class,
                    TableInfo.class,
                    FlexConfiguration.class,
                    FlexSqlSessionFactoryBuilder.class,
                    CPI.class
            ).forEach(x -> hints.reflection().registerType(x, ALL_MEMBERS));

            // 注册 XML 资源文件匹配
            Stream.of(
                    "org/apache/ibatis/builder/xml/*.dtd",
                    "org/apache/ibatis/builder/xml/*.xsd"
            ).forEach(hints.resources()::registerPattern);

            // 注册 JDK 动态代理类
            hints.proxies().registerJdkProxy(StatementHandler.class);
            hints.proxies().registerJdkProxy(Executor.class);
            hints.proxies().registerJdkProxy(ResultSetHandler.class);
            hints.proxies().registerJdkProxy(ParameterHandler.class);

            // 注册 Statement 相关底层类
            hints.reflection().registerType(BoundSql.class, MemberCategory.ACCESS_DECLARED_FIELDS);
            hints.reflection().registerType(RoutingStatementHandler.class, MemberCategory.ACCESS_DECLARED_FIELDS);
            hints.reflection().registerType(BaseStatementHandler.class, MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }

    static class MyBatisFlexBeanFactoryInitializationAotProcessor
            implements BeanFactoryInitializationAotProcessor, BeanRegistrationExcludeFilter {

        private final Set<Class<?>> excludeClasses = new HashSet<>();

        MyBatisFlexBeanFactoryInitializationAotProcessor() {
            excludeClasses.add(MapperScannerConfigurer.class);
        }

        @Override
        public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
            return excludeClasses.contains(registeredBean.getBeanClass());
        }

        @Override
        public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
            String[] beanNames = beanFactory.getBeanNamesForType(MapperFactoryBean.class);
            if (beanNames.length == 0) {
                return null;
            }
            return (context, code) -> {
                RuntimeHints hints = context.getRuntimeHints();
                for (String beanName : beanNames) {
                    String realBeanName = beanName.startsWith("&") ? beanName.substring(1) : beanName;
                    if (!beanFactory.containsBeanDefinition(realBeanName)) {
                        continue;
                    }
                    BeanDefinition beanDefinition = beanFactory.getBeanDefinition(realBeanName);
                    PropertyValue mapperInterface = beanDefinition.getPropertyValues().getPropertyValue("mapperInterface");
                    if (mapperInterface != null && mapperInterface.getValue() != null) {
                        Class<?> mapperInterfaceType = null;
                        Object val = mapperInterface.getValue();
                        if (val instanceof Class<?>) {
                            mapperInterfaceType = (Class<?>) val;
                        } else if (val instanceof String) {
                            try {
                                mapperInterfaceType = ClassUtils.forName((String) val, beanFactory.getBeanClassLoader());
                            } catch (ClassNotFoundException e) {
                                // ignore
                            }
                        }

                        if (mapperInterfaceType != null) {
                            registerReflectionTypeIfNecessary(mapperInterfaceType, hints);
                            hints.proxies().registerJdkProxy(mapperInterfaceType);
                            hints.resources()
                                    .registerPattern(mapperInterfaceType.getName().replace('.', '/').concat(".xml"));
                            registerMapperRelationships(mapperInterfaceType, hints);
                        }
                    }
                }
            };
        }

        private void registerMapperRelationships(Class<?> mapperInterfaceType, RuntimeHints hints) {
            Method[] methods = ReflectionUtils.getAllDeclaredMethods(mapperInterfaceType);
            for (Method method : methods) {
                if (method.getDeclaringClass() != Object.class) {
                    ReflectionUtils.makeAccessible(method);
                    registerSqlProviderTypes(method, hints, SelectProvider.class, SelectProvider::value, SelectProvider::type);
                    registerSqlProviderTypes(method, hints, InsertProvider.class, InsertProvider::value, InsertProvider::type);
                    registerSqlProviderTypes(method, hints, UpdateProvider.class, UpdateProvider::value, UpdateProvider::type);
                    registerSqlProviderTypes(method, hints, DeleteProvider.class, DeleteProvider::value, DeleteProvider::type);
                    Class<?> returnType = MyBatisMapperTypeUtils.resolveReturnClass(mapperInterfaceType, method);
                    registerReflectionTypeIfNecessary(returnType, hints);
                    MyBatisMapperTypeUtils.resolveParameterClasses(mapperInterfaceType, method)
                            .forEach(x -> registerReflectionTypeIfNecessary(x, hints));
                }
            }
        }

        @SafeVarargs
        private final <T extends Annotation> void registerSqlProviderTypes(
                Method method, RuntimeHints hints, Class<T> annotationType, Function<T, Class<?>>... providerTypeResolvers) {
            for (T annotation : method.getAnnotationsByType(annotationType)) {
                for (Function<T, Class<?>> providerTypeResolver : providerTypeResolvers) {
                    registerReflectionTypeIfNecessary(providerTypeResolver.apply(annotation), hints);
                }
            }
        }

        private void registerReflectionTypeIfNecessary(Class<?> type, RuntimeHints hints) {
            if (type != null && !type.isPrimitive() && !type.getName().startsWith("java")) {
                hints.reflection().registerType(type, ALL_MEMBERS);
            }
        }
    }

    static class MyBatisMapperTypeUtils {
        private MyBatisMapperTypeUtils() {
        }

        static Class<?> resolveReturnClass(Class<?> mapperInterface, Method method) {
            Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
            return typeToClass(resolvedReturnType, method.getReturnType());
        }

        static Set<Class<?>> resolveParameterClasses(Class<?> mapperInterface, Method method) {
            return Stream.of(TypeParameterResolver.resolveParamTypes(method, mapperInterface))
                    .map(x -> typeToClass(x, x instanceof Class ? (Class<?>) x : Object.class)).collect(Collectors.toSet());
        }

        private static Class<?> typeToClass(Type src, Class<?> fallback) {
            Class<?> result = null;
            if (src instanceof Class<?>) {
                if (((Class<?>) src).isArray()) {
                    result = ((Class<?>) src).getComponentType();
                } else {
                    result = (Class<?>) src;
                }
            } else if (src instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) src;
                int index = (parameterizedType.getRawType() instanceof Class
                        && Map.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())
                        && parameterizedType.getActualTypeArguments().length > 1) ? 1 : 0;
                Type actualType = parameterizedType.getActualTypeArguments()[index];
                result = typeToClass(actualType, fallback);
            }
            if (result == null) {
                result = fallback;
            }
            return result;
        }
    }

    static class MyBatisFlexMapperFactoryBeanPostProcessor implements MergedBeanDefinitionPostProcessor, BeanFactoryAware {

        private static final String MAPPER_FACTORY_BEAN = "org.mybatis.spring.mapper.MapperFactoryBean";
        private ConfigurableBeanFactory beanFactory;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = (ConfigurableBeanFactory) beanFactory;
        }

        @Override
        public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
            if (ClassUtils.isPresent(MAPPER_FACTORY_BEAN, this.beanFactory.getBeanClassLoader())) {
                resolveMapperFactoryBeanTypeIfNecessary(beanDefinition);
            }
        }

        private void resolveMapperFactoryBeanTypeIfNecessary(RootBeanDefinition beanDefinition) {
            if (!beanDefinition.hasBeanClass() || !MapperFactoryBean.class.isAssignableFrom(beanDefinition.getBeanClass())) {
                return;
            }

            // 核心修复逻辑：
            // AOT 编译器不支持 BY_TYPE 模式，因此若发现未显式注入 sqlSessionFactory 或 sqlSessionTemplate，
            // 则在此手动向 BeanDefinition 中增加对 sqlSessionFactory 的硬引用依赖，强制 AOT 生成正确的静态注入逻辑。
            if (!beanDefinition.getPropertyValues().contains("sqlSessionFactory")
                    && !beanDefinition.getPropertyValues().contains("sqlSessionTemplate")) {
                beanDefinition.getPropertyValues().add("sqlSessionFactory", new RuntimeBeanReference("sqlSessionFactory"));
            }

            if (beanDefinition.getResolvableType().hasUnresolvableGenerics()) {
                Class<?> mapperInterface = getMapperInterface(beanDefinition);
                if (mapperInterface != null) {
                    ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
                    constructorArgumentValues.addGenericArgumentValue(mapperInterface);
                    beanDefinition.setConstructorArgumentValues(constructorArgumentValues);
                    beanDefinition.setTargetType(ResolvableType.forClassWithGenerics(beanDefinition.getBeanClass(), mapperInterface));
                }
            }
        }

        private Class<?> getMapperInterface(RootBeanDefinition beanDefinition) {
            try {
                Object value = beanDefinition.getPropertyValues().get("mapperInterface");
                if (value instanceof Class<?>) {
                    return (Class<?>) value;
                } else if (value instanceof String) {
                    return ClassUtils.forName((String) value, this.beanFactory.getBeanClassLoader());
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }
    }
}