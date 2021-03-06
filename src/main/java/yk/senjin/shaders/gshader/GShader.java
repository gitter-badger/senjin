package yk.senjin.shaders.gshader;

import myengine.optiseq.states.arraystructure.AbstractArrayStructure;
import myengine.optiseq.states.arraystructure.VBOVertexAttrib;
import org.lwjgl.opengl.GL11;
import yk.jcommon.collections.YList;
import yk.jcommon.collections.YMap;
import yk.jcommon.collections.YSet;
import yk.jcommon.fastgeom.Vec2f;
import yk.jcommon.fastgeom.Vec3f;
import yk.jcommon.fastgeom.Vec4f;
import yk.jcommon.utils.BadException;
import yk.senjin.AbstractState;
import yk.senjin.shaders.ShaderHandler;
import yk.senjin.shaders.UniformVariable;
import yk.senjin.shaders.VertexAttrib;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Map;

import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static yk.jcommon.collections.YArrayList.al;
import static yk.jcommon.collections.YHashMap.hm;
import static yk.jcommon.collections.YHashSet.hs;
import static yk.senjin.VertexStructureState.assertType;

/**
 * Created with IntelliJ IDEA.
 * User: yuri
 * Date: 14/12/14
 * Time: 18:46
 */
public class GShader extends AbstractState {

    public ShaderHandler shader;
    private ProgramGenerator pvs;
    private ProgramGenerator pfs;

    private ReflectionVBO currentVBO;

    public Object vs;

    public GShader(ShaderParent vs, ShaderParent fs) {
        this.vs = vs;
        init(vs, fs);
    }

    public GShader(String srcDir, Object vs, Object fs) {
        this.vs = vs;
        init(srcDir, vs, fs);
    }

    public void init(Object vs, Object fs) {
        init("src/main/java/", vs, fs);
    }

    public void init(String srcDir, Object vs, Object fs) {
        pvs = createProgram(srcDir, vs, "vs");
        pfs = createProgram(srcDir, fs, "fs");
        if (pvs.outputClass != pfs.inputClass) throw new Error("output of VS " + pvs.outputClass.getName() + " must be same as input to FS " + pfs.inputClass.getName());
        if (!BaseVSOutput.class.isAssignableFrom(pvs.outputClass)) throw new Error("output of VS must extends BaseVSOutput");
        if (pfs.outputClass != StandardFrame.class) throw new Error("output of FS must be StandardFrame class");

        Map<String, String> seenAt = hm();
        for (VertexAttrib a : pvs.attributes) {
            System.out.println("checking " + a.getName());
            String old = seenAt.put(a.getName(), "VS input");
            if (old != null) throw new Error("name clash for " + a.getName() + " at " + old + " and " + seenAt.get(a.getName()));
        }
        for (String a : pfs.varyingFS) {
            System.out.println("checking " + a);
            String old = seenAt.put(a, "VS output");
            if (old != null) throw new Error("name clash for " + a + " at " + old + " and " + seenAt.get(a));
        }
        for (UniformVariable a : pvs.uniforms) {
            System.out.println("checking " + a.name);
            String old = seenAt.put(a.name, "VS uniforms");
            if (old != null) {
                throw new Error("name clash for " + a.name + " at " + old + " and " + seenAt.get(a.name));
            }
        }
        for (Iterator<UniformVariable> iterator = pfs.uniforms.iterator(); iterator.hasNext(); ) {
            UniformVariable a = iterator.next();
            System.out.println("checking " + a.name);
            String old = seenAt.put(a.name, "FS uniforms");
//            if (old != null) throw new Error("name clash for " + a.name + " at " + old + " and " + seenAt.get(a.name));
            if (old != null) {
                //TODO check same type
                //TODO prevent somehow from filling from FS
                iterator.remove();
            }
        }




        shader = new ShaderHandler();
        //

        String res = "";
        //for (String s : pfs.getVaryingFS()) res += s;
        //pvs.setResultSrc(res + pvs.getResultSrc());

        System.out.println(pvs.resultSrc);
        System.out.println(pfs.resultSrc);
        //
        for (UniformVariable u : pvs.uniforms) shader.addVariables(u);
        for (VertexAttrib v : pvs.attributes) shader.addVertexAttrib(v);

        for (UniformVariable u : pfs.uniforms) shader.addVariables(u);
        //for (VertexAttrib v : pfs.getVarying()) shader.addVertexAttrib(v);

        shader.createFromSrc(pvs.resultSrc, pfs.resultSrc);
    }

    public static ProgramGenerator createProgram(String srcDir, Object vs, String programType) {
        String path1 = vs.getClass().getName();
        path1 = srcDir + path1.replace(".", "/") + ".groovy";
        System.out.println(path1);
        return new ProgramGenerator(path1, vs, programType);
    }

    private YMap<Class, YList<AbstractArrayStructure>> type2structure = hm();
    private YList<AbstractArrayStructure> getShaderSpecificStructure(Class clazz) {
        YList<AbstractArrayStructure> result = type2structure.get(clazz);
        if (result == null) {
            result = al();
            Field[] fields = clazz.getDeclaredFields();
            int stride = ReflectionVBO.getSizeOfType(clazz);
            int offset = 0;
            YSet<String> hasFields = hs();
            for (Field field : fields) {
                if (Modifier.isTransient(field.getModifiers())) continue;
                hasFields.add(field.getName());
                VertexAttrib shaderAttrib = shader.getVertexAttrib(field.getName());
                if (shaderAttrib == null) throw new RuntimeException("shader has no attribute " + field.getName());

                result.add(new VBOVertexAttrib(shaderAttrib.getIndex(), shaderAttrib.getSize(), shaderAttrib.getType(), shaderAttrib.isNormalized(), stride, offset));

                if (field.getType() == Vec2f.class) {
                    assertType(shaderAttrib, 2, GL11.GL_FLOAT, field.getName());
                    offset += 2 * 4;
                } else if (field.getType() == Vec3f.class) {
                    assertType(shaderAttrib, 3, GL11.GL_FLOAT, field.getName());
                    offset += 3 * 4;
                } else if (field.getType() == Vec4f.class) {
                    assertType(shaderAttrib, 4, GL11.GL_FLOAT, field.getName());
                    offset += 4 * 4;
                } else throw BadException.die("unknown VS input field type: " + field.getType());
            }
            for (String attrib : shader.vertexAttribs.keySet()) if (!hasFields.contains(attrib)) throw new Error("buffer haven't field " + attrib);
            type2structure.put(clazz, result);
        }
        return result;
    }

    private YList<AbstractArrayStructure> currentStructure;
    //TODO interface
    public void setInput(ReflectionVBO vbo) {
        currentVBO = vbo;
        currentStructure = getShaderSpecificStructure(vbo.inputType);
    }

    @Override
    public void enable() {
        shader.enable();
        glBindBuffer(GL_ARRAY_BUFFER, currentVBO.bufferId);
        for (int i = 0; i < currentStructure.size(); i++) currentStructure.get(i).turnOn();
    }

    @Override
    public void disable() {
        for (int i = 0; i < currentStructure.size(); i++) currentStructure.get(i).turnOff();
        shader.disable();
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

}
