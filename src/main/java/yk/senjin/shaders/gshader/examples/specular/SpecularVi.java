package yk.senjin.shaders.gshader.examples.specular;

import yk.jcommon.fastgeom.Vec2f;
import yk.jcommon.fastgeom.Vec3f;

/**
 * Created with IntelliJ IDEA.
 * User: yuri
 * Date: 22/10/15
 * Time: 20:55
 */
public class SpecularVi {

    public Vec3f normal;
    public Vec3f pos;
    public Vec2f uv;

    public SpecularVi(Vec3f pos, Vec3f normal, Vec2f uv) {
        this.pos = pos;
        this.normal = normal;
        this.uv = uv;
    }
}
