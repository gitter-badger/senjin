package yk.senjin.shaders.gshader

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilePhase
import yk.jcommon.collections.YList
import yk.jcommon.utils.IO
import yk.jcommon.utils.Reflector
import yk.senjin.shaders.UniformVariable
import yk.senjin.shaders.VertexAttrib

import java.lang.reflect.Modifier

import static org.lwjgl.opengl.GL11.GL_FLOAT
import static org.lwjgl.opengl.GL11.GL_INT
import static yk.jcommon.collections.YArrayList.al
import static yk.jcommon.collections.YArrayList.toYList
import static yk.jcommon.collections.YHashMap.hm
import static yk.jcommon.collections.YHashSet.hs

/**
 * Created with IntelliJ IDEA.
 * User: yuri
 * Date: 14/12/14
 * Time: 17:10
 */
//TODO runtime data filling, without generated VBOs, with reflection checking (slow, but simple for many little tests)
//TODO generate code for uniforms filling
class ProgramGenerator {
    public final Object data
    public final String src
    public String resultSrc

    public YList<UniformVariable> uniforms = al()
    public YList<VertexAttrib> attributes = al()

    public YList<String> varyingFS = al()
    public Class inputClass
    public Class outputClass
    private String inputName
    private String outputName



    static void main(String[] args) {
//        println new ProgramGenerator("/home/yuri/1/myproject/senjin/src/myengine/optiseq/states/shaders/gshader/VertexShader.groovy", new VertexShader(), "vs").resultSrc
//        println new ProgramGenerator("/home/yuri/1/myproject/senjin/src/myengine/optiseq/states/shaders/gshader/CustomFragmentShader.groovy", new CustomFragmentShader(), "fs").resultSrc
    }

    ProgramGenerator(String src, Object data, String programType) {
        this.data = data
        this.src = src
        this.resultSrc = translate(programType)

    }

    private Set<String> glNames = hs("texture")

    private String programType;
    private String translate(String programType) {
        //TODO different versions
        String result = "#version 130\n"
        result += "\n//autogenerated from " + src + "\n\n"
        this.programType = programType;

//        new VertexShader().m()

        List<ASTNode> nodes = new AstBuilder().buildFromString(CompilePhase.INSTRUCTION_SELECTION, IO.readFile(src))

        def mainClass = (ClassNode) nodes[1]


        for (MethodNode m in mainClass.getDeclaredMethods("main")) {
            if (Modifier.isStatic(m.modifiers) || (m.modifiers & 0x00001000) != 0) continue
            Object input = m.parameters[0]
            inputName = input.name
            Object output = m.parameters[1]
            outputName = output.name
            inputClass = Class.forName(input.type.name)
            outputClass = Class.forName(output.type.name)
        }
        if (programType.equals("fs") && outputClass != StandardFrame.class) throw new Error("Fragment shader must have ${StandardFrame.class.getSimpleName()} output type, but it is " + outputClass)

        for (fn in inputClass.getDeclaredFields()) {
            if (Modifier.isTransient(fn.getModifiers())) continue
            if (glNames.contains(fn.name)) throw new Error("clash with gl names: " + fn.name + " in input data for " + programType)
            def type = translateType(fn.type.name)
            if ("vs".equals(programType)) {
                result += "in " + type + " " + fn.name + ";\n"
                if (type.equals("vec4")) attributes.add(new VertexAttrib(fn.name, GL_FLOAT, 4))
                else if (type.equals("vec3")) attributes.add(new VertexAttrib(fn.name, GL_FLOAT, 3))
                else if (type.equals("vec2")) attributes.add(new VertexAttrib(fn.name, GL_FLOAT, 2));
                else if (type.equals("float")) attributes.add(new VertexAttrib(fn.name, GL_FLOAT, 1));
                else if (type.equals("int")) attributes.add(new VertexAttrib(fn.name, GL_INT, 1));
                else throw new RuntimeException("unknown varying type " + type)
            }
            if ("fs".equals(programType)) {
                result += "in " + type + " " + fn.name + "_fi;\n"
                varyingFS.add(fn.name + "_fi")
            }
        }

        for (fn in outputClass.getDeclaredFields()) {
            if (glNames.contains(fn.name)) throw new Error("clash with gl names: " + fn.name + " in output data for " + programType)
            def type = translateType(fn.type.name)
            if ("fs".equals(programType)) {
                result += "out " + type + " " + fn.name + ";\n"
            } else {
                result += "out " + type + " " + fn.name + "_fi;\n"
            }
        }

        for (o in mainClass.getFields()) {
            FieldNode fn = o
            if (fn.name.contains("\$") || fn.name.startsWith("__timeStamp") || fn.name.equals("metaClass")) continue

            if (glNames.contains(fn.name)) throw new Error("clash with gl names: " + fn.name + " in uniforms for " + programType)

            def type = translateType(fn.type.name)
            if (Modifier.isFinal(fn.modifiers)) {
                result += "const " + type + " " + fn.name + " = " + translateExpression(((FieldNode) fn).initialExpression) + ";\n"
            } else if ((fn.modifiers & Modifier.STATIC) == 0) {
                if (type.endsWith("[]")) {
                    def sizeExpression = translateExpression(((ArrayExpression) fn.initialExpression).sizeExpression.get(0))
                    result += "uniform " + type.substring(0, type.length() - 2) + " " + fn.name + "[" + sizeExpression + "];\n"
                } else {
                    result += "uniform " + type + " " + fn.name + ";\n"
                }

                def field = data.class.getDeclaredField(fn.name)
                Object dataInstance = data.class.newInstance()

                field.setAccessible(true)
                Object got = field.get(data)
                if (type.equals("vec4")) uniforms.add(new UniformRefVec4f(fn.name, data, fn.name))
//                if (type.equals("vec4")) uniforms.add(new Uniform4fG(fn.name, (Vec4f) got))
                else if (type.equals("sampler2D")) {
                    def sampler = (Sampler2D) got
                    sampler.name = fn.name
                    uniforms.add(sampler)
                }
                else if (type.equals("vec2")) uniforms.add(new UniformRefVec2f(fn.name, data, fn.name))
                else if (type.equals("vec3")) uniforms.add(new UniformRefVec3f(fn.name, data, fn.name))
                else if (type.equals("int")) uniforms.add(new UniformRefInt(fn.name, data, fn.name))
                else if (type.equals("float")) uniforms.add(new UniformRefFloat(fn.name, data, fn.name))
                    //TODO array size
                else if (type.equals("float[]")) {
                    uniforms.add(new UniformRefFloatArray(fn.name, data, fn.name, ((float[])Reflector.get(dataInstance, fn.name)).length))
                }
                else if (type.equals("mat3")) uniforms.add(new UniformRefMatrix3(fn.name, data, fn.name))
                else if (type.equals("mat4")) uniforms.add(new UniformRefMatrix4(fn.name, data, fn.name))
                else throw new RuntimeException("unknown uniform type " + type)
            }
        }

        result += "\nvoid main(void) "

        for (m in mainClass.getDeclaredMethods("main")) {
            if (Modifier.isStatic(m.modifiers) || (m.modifiers & 0x00001000) != 0) continue
            result += translateExpression(m.code)
        }

        result += ("\n")

//        result += "uniforms: " + uniforms + "\n"
//        result += "varying: " + varying + "\n"
        result
    }
    private int indentation = 0

    private String tab() {
        String result = ""
        for (int i = 0; i < indentation; i++) result += "    "
        return result;
    }

    private String translateExpression(Object o) {
        println("" + o)
        if (o instanceof ExpressionStatement) return translateExpression((ExpressionStatement)o)
        if (o instanceof DeclarationExpression) return translateExpression((DeclarationExpression)o)
        if (o instanceof VariableExpression) return translateExpression((VariableExpression)o)
        if (o instanceof BinaryExpression) return translateExpression((BinaryExpression)o)
        if (o instanceof UnaryMinusExpression) return translateExpression((UnaryMinusExpression)o)
        if (o instanceof PropertyExpression) return translateExpression((PropertyExpression)o)
        if (o instanceof ReturnStatement) return translateExpression(((ReturnStatement)o).expression)
        if (o instanceof MethodCallExpression) return translateExpression((MethodCallExpression) o)
        if (o instanceof ArgumentListExpression) return translateExpression((ArgumentListExpression) o)
        if (o instanceof ConstantExpression) return translateExpression((ConstantExpression)o)
        if (o instanceof StaticMethodCallExpression) return translateExpression((StaticMethodCallExpression)o)
        if (o instanceof ClassExpression) return translateExpression((ClassExpression)o)
        if (o instanceof IfStatement) return translateExpression((IfStatement)o)
        if (o instanceof BooleanExpression) return translateExpression((BooleanExpression)o)
        if (o instanceof CastExpression) return translateExpression((CastExpression)o)
        if (o instanceof ForStatement) return translateExpression((ForStatement)o)
        if (o instanceof PostfixExpression) return translateExpression((PostfixExpression)o)
        if (o instanceof BlockStatement) return translateExpression((BlockStatement)o)
        return ":"+o
    }

    private String translateExpression(ConstantExpression e) {
        return convertions.containsKey(e.text) ? convertions.get(e.text) : e.text
    }

    private String translateExpression(DeclarationExpression e) {
        def result = translateVarDecl((VariableExpression) e.leftExpression)
        if (!(e.rightExpression instanceof EmptyExpression)) result += " = " + translateExpression(e.rightExpression)
        return result
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private String translateExpression(ClassExpression e) {
        if (e.type.name.endsWith("myengine.optiseq.states.shaders.gshader.ShaderFunctions")) return ""
        throw new Error("don't know what to do with " + e)
    }

    private String translateExpression(StaticMethodCallExpression e) {
        return e.methodAsString + "(" + translateExpression(e.arguments) + ")"
    }

    private String translateExpression(BlockStatement e) {
        String result = "{\n"
        indentation++
        for (Statement s : e.statements) {
            result += tab() + translateExpression(s)
            if (!(s instanceof IfStatement)) result += ";"
            result += "\n"
        }
        indentation--
        result += tab() + "}"
        return result
    }

    private String translateExpression(BooleanExpression e) {
        return translateExpression(e.expression)
    }

    private String translateExpression(CastExpression e) {
        return e.type.name + "(" + translateExpression(e.expression) + ")"
    }

    private String translateExpression(ForStatement e) {
        def expressions = ((ClosureListExpression) (e.collectionExpression)).expressions
        String result = "for (" + translateExpression(expressions.get(0)) + "; " + translateExpression(expressions.get(1)) + "; " + translateExpression(expressions.get(2)) + ") {\n"
        result += "    " + translateExpression(e.loopBlock)
        result += ";\n    }"
        return result
    }

    private String translateExpression(PostfixExpression e) {
        return translateExpression(e.expression) + e.operation.getText()
    }

    private String translateExpression(IfStatement e) {
        def result = "if (" + translateExpression(e.booleanExpression) + ") " + translateExpression(e.ifBlock)
        if (!(e.ifBlock instanceof BlockStatement)) result += ";"
        if (!(e.elseBlock instanceof EmptyStatement)) {
            result += " else " + translateExpression(e.elseBlock)
            if (!(e.elseBlock instanceof BlockStatement)) result += ";"
        }
        return result
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private String translateExpression(VariableExpression e) {
        return e.name
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private String translateVarDecl(VariableExpression e) {
        return translateType(e.getType().name) + " " + e.name
    }


    private String translateExpression(BinaryExpression e) {
        def opName = e.operation.getText()
        if (opName == "[") return translateExpression(e.leftExpression) + "[" + translateExpression(e.rightExpression) + "]"
        return translateExpression(e.leftExpression) + " " + opName + " " + translateExpression(e.rightExpression)
    }

    private String translateExpression(UnaryMinusExpression e) {
        return "-" + translateExpression(e.expression)
    }

    private String translateExpression(ExpressionStatement e) {
        return translateExpression(e.expression)
    }

    private String translateExpression(ArgumentListExpression e) {
        return toYList(e.getExpressions()).map{ee -> translateExpression(ee)}.toString(", ")
    }

    private String translateExpression(MethodCallExpression e) {
        return translateExpression(e.method) + "(" + translateExpression(e.arguments) + ")"
    }

    private String translateExpression(PropertyExpression e) {
        String obj = translateExpression(e.objectExpression)
        if (obj.equals(outputName) && programType.equals("vs")) return fiName(e.propertyAsString)
        if (obj.equals(inputName) && programType.equals("fs")) return fiName(e.propertyAsString)
        if (obj.equals(outputName)) return e.propertyAsString
        if (obj.equals(inputName)) return e.propertyAsString
        if (obj.equals("")) return e.propertyAsString
        if ((e.objectExpression instanceof BinaryExpression)) return "(" + obj + ")." + e.propertyAsString
        return obj + "." + e.propertyAsString
    }

    private static String fiName(String prop) {//TODO check names on BaseVSOutput by reflection
        if (prop.equals("gl_Position")) return prop;
        return prop + "_fi";
    }

    static Map<String, String> convertions = hm(
            "[F", "float[]",
            "float", "float",
            "Integer", "int",
            "int", "int",
            "Matrix4", "mat4",
            "Matrix3", "mat3",
            "Vec2f", "vec2",
            "Vec3f", "vec3",
            "Vec4f", "vec4",
            "Vec3", "vec3",
            "Vec2", "vec2",
            "Sampler2D", "sampler2D",
            "Float", "float")
    static String translateType(String groovyType) {
        def t = groovyType.split("\\.").last()
        if (!convertions.containsKey(t)) throw new RuntimeException("unknown type " + t)
        return convertions.get(t)
    }


}
