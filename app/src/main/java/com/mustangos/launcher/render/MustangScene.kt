package com.mustangos.launcher.render

import com.mustangos.core.MustangPaint
import com.mustangos.core.MustangProfile
import com.mustangos.core.MustangVersion
import kotlin.math.*

/** Column-major 4x4 matrices, as OpenGL expects. */
object Mat4 {
    fun identity()=FloatArray(16).also { it[0]=1f; it[5]=1f; it[10]=1f; it[15]=1f }
    fun multiply(a: FloatArray,b: FloatArray): FloatArray {
        val r=FloatArray(16)
        for(c in 0..3) for(row in 0..3) { var s=0f; for(k in 0..3) s+=a[k*4+row]*b[c*4+k]; r[c*4+row]=s }
        return r
    }
    fun translate(x: Float,y: Float,z: Float)=identity().also { it[12]=x; it[13]=y; it[14]=z }
    fun scale(x: Float,y: Float,z: Float)=identity().also { it[0]=x; it[5]=y; it[10]=z }
    fun rotateY(a: Float)=identity().also { it[0]=cos(a); it[2]=-sin(a); it[8]=sin(a); it[10]=cos(a) }
    fun rotateZ(a: Float)=identity().also { it[0]=cos(a); it[1]=sin(a); it[4]=-sin(a); it[5]=cos(a) }
    fun perspective(fovY: Float,aspect: Float,near: Float,far: Float): FloatArray {
        val f=1f/tan(fovY/2); val r=FloatArray(16)
        r[0]=f/aspect; r[5]=f; r[10]=(far+near)/(near-far); r[11]=-1f; r[14]=2*far*near/(near-far)
        return r
    }
    fun lookAt(eye: V,target: V,up: V): FloatArray {
        val f=(target-eye).normalized(); val s=f.cross(up).normalized(); val u=s.cross(f)
        val r=identity()
        r[0]=s.x; r[4]=s.y; r[8]=s.z
        r[1]=u.x; r[5]=u.y; r[9]=u.z
        r[2]=-f.x; r[6]=-f.y; r[10]=-f.z
        r[12]=-s.dot(eye); r[13]=-u.dot(eye); r[14]=f.dot(eye)
        return r
    }
    fun normal(m: FloatArray)=floatArrayOf(m[0],m[1],m[2],m[4],m[5],m[6],m[8],m[9],m[10])
}

/** Everything the renderer needs per frame; computed without GL so it can be tested and previewed. */
data class SceneState(
    val yaw: Float=DEFAULT_YAW, val pitch: Float=.20f, val leftDoor: Float=0f, val rightDoor: Float=0f,
    val lights: Float=0f, val travelMeters: Float=0f, val steer: Float=.14f
) { companion object { const val DEFAULT_YAW=.62f } }

class DrawCall(val mesh: Int,val model: FloatArray,val material: Material,val base: FloatArray,val transparent: Boolean)
class Glow(val center: V,val color: FloatArray,val size: Float)
class Frame(val viewProj: FloatArray,val eye: V,val right: V,val up: V,val calls: List<DrawCall>,val glows: List<Glow>)

class MustangScene(val profile: MustangProfile) {
    val model=MustangModel(profile)
    /** Mesh list: body parts first, then the shared wheel parts. */
    val meshes: List<MeshPart> = model.parts+model.wheel
    private val spec=model.spec
    val paint: FloatArray = linear(profile.paint.argb).let { c -> if(profile.paint==MustangPaint.BLACK) floatArrayOf(.012f,.013f,.016f) else c }
    private val caliper: FloatArray = when(profile.version) {
        MustangVersion.GT,MustangVersion.BULLITT,MustangVersion.SHELBY,MustangVersion.SHELBY_GT350,MustangVersion.SHELBY_GT500,MustangVersion.DARK_HORSE,MustangVersion.BOSS_302,MustangVersion.MACH_1 ->
            floatArrayOf(.42f,.012f,.014f)
        else -> floatArrayOf(.035f,.036f,.04f)
    }
    val bodyX=floatArrayOf(spec.frontX,spec.rearX,spec.cowlX,spec.deckX)
    val bodyY=floatArrayOf(spec.bottom,spec.beltMid,spec.halfWidth,spec.axle)
    val door=floatArrayOf(spec.doorFrontX,spec.doorRearX)
    val style=spec.style.toFloat()
    val floorCar=floatArrayOf((spec.frontX+spec.rearX)/2,spec.length/2,spec.halfWidth,spec.axle)
    val floorTrack=floatArrayOf(spec.track,spec.frontX)

    fun frame(state: SceneState,width: Int,height: Int): Frame {
        val aspect=width.toFloat()/height.coerceAtLeast(1)
        val fov=Math.toRadians(24.0).toFloat()
        // Fit the car's length horizontally and its height vertically, with a little air around it.
        val halfX=tan(fov/2)*aspect
        val distance=max(2.75f/halfX,1.55f/tan(fov/2))+.6f
        val target=V((spec.frontX+spec.rearX)/2,.58f,0f)
        val eye=target+V(cos(state.pitch)*cos(state.yaw),sin(state.pitch),cos(state.pitch)*sin(state.yaw))*distance
        val view=Mat4.lookAt(eye,target,V(0f,1f,0f))
        val viewProj=Mat4.multiply(Mat4.perspective(fov,aspect,.5f,60f),view)
        val right=V(view[0],view[4],view[8]); val up=V(view[1],view[5],view[9])
        val calls=ArrayList<DrawCall>()
        val opening=Material.GLASS
        model.parts.forEachIndexed { index,part ->
            val matrix=when(part.part) {
                Part.DOOR_LEFT -> doorMatrix(1f,state.leftDoor)
                Part.DOOR_RIGHT -> doorMatrix(-1f,state.rightDoor)
                else -> Mat4.identity()
            }
            calls.add(DrawCall(index,matrix,part.material,color(part.material),part.material==opening))
        }
        val spin=-state.travelMeters/spec.tireR
        for(front in listOf(true,false)) for(left in listOf(true,false)) {
            val x=if(front) spec.axle else -spec.axle
            val z=if(left) spec.track else -spec.track
            val steer=if(front) state.steer else 0f
            var base=Mat4.multiply(Mat4.translate(x,spec.tireR,z),Mat4.rotateY(steer))
            if(!left) base=Mat4.multiply(base,Mat4.scale(1f,1f,-1f))
            model.wheel.forEachIndexed { k,part ->
                val m=if(part.part==Part.CALIPER) base else Mat4.multiply(base,Mat4.rotateZ(spin))
                calls.add(DrawCall(model.parts.size+k,m,part.material,color(part.material),false))
            }
        }
        val glows=ArrayList<Glow>()
        if(state.lights>.01f) for(s in floatArrayOf(1f,-1f)) {
            // Lamps glow only towards the side they face.
            fun facing(c: V,forward: Float)=((eye-c).normalized().x*forward).coerceIn(0f,1f).pow(.7f)*state.lights
            val head=V(spec.frontX+.03f,.67f,s*.70f); val tail=V(spec.rearX-.03f,if(spec.style<3) .66f else .80f,s*.62f)
            val h=facing(head,1f); val t=facing(tail,-1f)
            if(h>.01f) glows.add(Glow(head,floatArrayOf(.9f*h,.95f*h,h),.55f))
            if(t>.01f) glows.add(Glow(tail,floatArrayOf(.9f*t,.03f*t,.02f*t),.42f))
        }
        return Frame(viewProj,eye,right,up,calls.sortedBy { it.transparent },glows)
    }
    private fun doorMatrix(side: Float,level: Float): FloatArray {
        val eased=level.coerceIn(0f,1f).let { it*it*(3-2*it) }
        val angle=side*eased*1.08f
        val hx=spec.doorFrontX; val hz=side*spec.halfWidth*.97f
        return Mat4.multiply(Mat4.multiply(Mat4.translate(hx,0f,hz),Mat4.rotateY(angle)),Mat4.translate(-hx,0f,-hz))
    }
    fun color(material: Material): FloatArray = when(material) {
        Material.PAINT -> paint
        Material.CALIPER -> caliper
        else -> floatArrayOf(0f,0f,0f)
    }
    companion object {
        fun linear(argb: Int): FloatArray = floatArrayOf(
            ((argb shr 16) and 255)/255f,((argb shr 8) and 255)/255f,(argb and 255)/255f).map { it.toDouble().pow(2.2).toFloat()*.85f }.toFloatArray()
    }
}
