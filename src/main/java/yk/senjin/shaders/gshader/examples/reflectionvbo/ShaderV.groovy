package yk.senjin.shaders.gshader.examples.reflectionvbo

import yk.jcommon.fastgeom.Matrix4
import yk.senjin.shaders.gshader.ShaderParent

/**
 * Created with IntelliJ IDEA.
 * User: yuri
 * Date: 09/06/15
 * Time: 20:22
 */
class ShaderV extends ShaderParent {
    public Matrix4 mvp = new Matrix4()

    def main(VSInput i, VSOutput o) {
        o.gl_Position = mvp * Vec4f(i.position, 1)
        o.pos = (mvp * Vec4f(i.position, 1)).xyz
    }

}
