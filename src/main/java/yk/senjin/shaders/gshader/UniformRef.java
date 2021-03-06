package yk.senjin.shaders.gshader;

import yk.jcommon.utils.Reflector;
import yk.senjin.DDDUtils;
import yk.senjin.shaders.UniformVariable;

import java.lang.reflect.Field;

/**
 * Created with IntelliJ IDEA.
 * User: yuri
 * Date: 21/10/15
 * Time: 13:02
 */
abstract public class UniformRef<T> extends UniformVariable {
    protected Object src;
    protected Field _field;



    public UniformRef(String name, Object src, String fieldName) {
        _field = Reflector.getField(src.getClass(), fieldName);
        this.src = src;
        this.name = name;
    }

    public T getValue() {
        try {
            return (T) _field.get(src);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
