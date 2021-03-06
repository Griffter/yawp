package io.yawp.repository;

import io.yawp.commons.http.RequestContext;
import io.yawp.commons.utils.JsonUtils;
import io.yawp.commons.utils.ReflectionUtils;
import io.yawp.repository.query.QueryBuilder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Feature {

    protected Repository yawp;

    protected RequestContext requestContext;

    public void setRepository(Repository yawp) {
        this.yawp = yawp;
        this.requestContext = yawp.getRequestContext();
    }

    public <T> QueryBuilder<T> yawp(Class<T> clazz) {
        return yawp.query(clazz);
    }

    public <T> QueryBuilder<T> yawpWithHooks(Class<T> clazz) {
        return yawp.queryWithHooks(clazz);
    }

    public boolean isOnRequest() {
        return requestContext != null;
    }

    public <T extends Feature> T feature(Class<T> clazz) {
        try {
            T feature = clazz.newInstance();
            feature.setRepository(yawp);
            return feature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> IdRef<T> id(Class<T> clazz, Long id) {
        return IdRef.create(yawp, clazz, id);
    }

    public <T> IdRef<T> id(Class<T> clazz, String name) {
        return IdRef.create(yawp, clazz, name);
    }

    public <T> T from(String json, Class<T> clazz) {
        return JsonUtils.from(yawp, json, clazz);
    }

    public <T> List<T> fromList(String json, Class<T> clazz) {
        return JsonUtils.fromList(yawp, json, clazz);
    }

    public <K, V> Map<K, V> fromMap(String json, Class<K> keyClazz, Class<V> valueClazz) {
        return JsonUtils.fromMap(yawp, json, keyClazz, valueClazz);
    }
    
    public String to(Object object) {
        return JsonUtils.to(object);
    }

    protected Map<String, Object> asMap(Object object) {
        try {
            Map<String, Object> map = new HashMap<>();
            List<Field> fields = ReflectionUtils.getFieldsRecursively(object.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                map.put(field.getName(), field.get(object));
            }
            return map;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
