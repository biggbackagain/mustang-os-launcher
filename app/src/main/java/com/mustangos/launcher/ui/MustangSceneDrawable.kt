package com.mustangos.launcher.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import com.mustangos.core.*
import kotlin.math.*

/** Original procedural 3D mesh, projected on Canvas. No web engine or external model downloads. */
class MustangSceneDrawable(val profile: MustangProfile) : Drawable() {
    var lights=0f
    var leftDoor=0f
    var rightDoor=0f
    var travelMeters=0f
    private data class V(val x: Float,val y: Float,val z: Float)
    private data class Face(val points: List<V>,val color: Int,val emissive: Boolean=false)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=.65f; color=0x284fc4ff }
    private val path=Path()
    private val yaw=.72f
    private val cy=cos(yaw); private val sy=sin(yaw)
    private val cp=cos(.32f); private val sp=sin(.32f)
    private val bodyColor=profile.paint.argb
    private val glass=0xff122c3e.toInt()
    private val trim=0xff101923.toInt()
    private val classic=MustangGeneration.forYear(profile.year).ordinal<=MustangGeneration.FOX.ordinal
    private val modern=MustangGeneration.forYear(profile.year).ordinal>=MustangGeneration.S550.ordinal
    private val roofHeight=if(classic) 1.57f else 1.52f
    private val staticFaces=buildBody()
    private fun face(color: Int,vararg points: V,emissive: Boolean=false)=Face(points.toList(),color,emissive)
    private fun project(v: V)=PointF(450+(v.x*cy+v.z*sy)*140,335-(v.y*cp-(-v.x*sy+v.z*cy)*sp)*140)
    private fun depth(v: V)=(-v.x*sy+v.z*cy)*cp+v.y*sp
    private fun shade(face: Face): Int {
        if(face.emissive) return face.color
        val a=face.points[0]; val b=face.points[1]; val c=face.points[2]
        val ux=b.x-a.x; val uy=b.y-a.y; val uz=b.z-a.z
        val vx=c.x-a.x; val vy=c.y-a.y; val vz=c.z-a.z
        val nx=uy*vz-uz*vy; val ny=uz*vx-ux*vz; val nz=ux*vy-uy*vx
        val length=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.001f)
        val light=.58f+.38f*abs(ny)/length+.12f*abs(nx)/length
        return Color.argb(Color.alpha(face.color),(Color.red(face.color)*light).toInt().coerceIn(0,255),
            (Color.green(face.color)*light).toInt().coerceIn(0,255),(Color.blue(face.color)*light).toInt().coerceIn(0,255))
    }
    private fun buildBody(): List<Face> = buildList {
        val cuts=listOf(-2.4f,-2.05f,-1.85f,-1.55f,-1.25f,-1f,-.72f,.83f,1f,1.25f,1.55f,1.85f,2.05f,2.35f)
        fun arch(x: Float): Float {
            val dx=min(abs(x+1.55f),abs(x-1.55f))
            return if(dx<.56f) .46f+sqrt(.56f*.56f-dx*dx) else .38f
        }
        for(side in listOf(-1f,1f)) for(i in 0 until cuts.lastIndex) {
            val x=cuts[i]; val next=cuts[i+1]
            if(x==-.72f && next==.83f) {
                add(face(trim,V(x,.4f,side*.92f),V(next,.4f,side*.92f),V(next,1.02f,side*.92f),V(x,1.02f,side*.92f)))
            } else add(face(bodyColor,V(x,arch(x),side*.96f),V(next,arch(next),side*.96f),V(next,1.02f,side*.96f),V(x,1.02f,side*.96f)))
            add(face(bodyColor,V(x,1.02f,side*.96f),V(next,1.02f,side*.96f),V(next,1.12f,side*.80f),V(x,1.12f,side*.80f)))
        }
        add(face(bodyColor,V(-2.4f,1.02f,-.96f),V(-.72f,1.12f,-.8f),V(-.72f,1.12f,.8f),V(-2.4f,1.02f,.96f)))
        add(face(bodyColor,V(.83f,1.12f,-.8f),V(2.35f,1.02f,-.96f),V(2.35f,1.02f,.96f),V(.83f,1.12f,.8f)))
        add(face(bodyColor,V(-2.4f,.38f,-.96f),V(-2.4f,.38f,.96f),V(-2.4f,1.02f,.96f),V(-2.4f,1.02f,-.96f)))
        add(face(bodyColor,V(2.35f,.38f,-.96f),V(2.35f,1.02f,-.96f),V(2.35f,1.02f,.96f),V(2.35f,.38f,.96f)))
        add(face(trim,V(-2.415f,.55f,-.58f),V(-2.415f,.55f,.58f),V(-2.415f,.92f,.58f),V(-2.415f,.92f,-.58f)))
        for(i in 0..5) {
            val z=-.48f+i*.19f
            add(face(0xff354b5b.toInt(),V(-2.42f,.57f,z),V(-2.42f,.89f,z),V(-2.42f,.89f,z+.018f),V(-2.42f,.57f,z+.018f)))
        }
        add(face(0xffd1e7f2.toInt(),V(-2.43f,.72f,-.13f),V(-2.43f,.82f,-.09f),V(-2.43f,.81f,.14f),V(-2.43f,.75f,.11f),emissive=true))
        add(face(trim,V(-2.43f,.32f,-.99f),V(-2.43f,.43f,-.99f),V(-2.43f,.43f,.99f),V(-2.43f,.32f,.99f)))
        add(face(glass,V(-.72f,1.12f,-.8f),V(-.24f,roofHeight,-.69f),V(-.24f,roofHeight,.69f),V(-.72f,1.12f,.8f)))
        val roofColor=if(profile.body==MustangBody.CONVERTIBLE) 0xff171e29.toInt() else bodyColor
        add(face(roofColor,V(-.24f,roofHeight,-.69f),V(.71f,roofHeight,-.69f),V(.71f,roofHeight,.69f),V(-.24f,roofHeight,.69f)))
        add(face(glass,V(.71f,roofHeight,-.69f),V(1.28f,1.12f,-.8f),V(1.28f,1.12f,.8f),V(.71f,roofHeight,.69f)))
        for(side in listOf(-1f,1f)) {
            add(face(roofColor,V(.56f,roofHeight,side*.69f),V(.71f,roofHeight,side*.69f),V(1.28f,1.12f,side*.8f),V(.56f,1.12f,side*.8f)))
            // Seat and sill are visible through an open door.
            add(face(trim,V(-.50f,.43f,side*.8f),V(.70f,.43f,side*.8f),V(.70f,.43f,side*.45f),V(-.50f,.43f,side*.45f)))
            add(face(0xff303c4c.toInt(),V(.30f,.5f,side*.4f),V(.50f,.5f,side*.4f),V(.50f,1.21f,side*.4f),V(.32f,1.21f,side*.4f)))
            for(i in 0..2) {
                val z=side*(.44f+i*.14f)
                add(face(0xffa32a3a.toInt(),V(2.365f,.75f,z),V(2.365f,.95f,z),V(2.365f,.95f,z+side*.065f),V(2.365f,.75f,z+side*.065f),emissive=true))
            }
        }
        if(profile.version.name.startsWith("SHELBY") || profile.version==MustangVersion.BOSS_302) for(z in listOf(-.25f,.1f)) {
            val stripe=if(profile.paint==MustangPaint.WHITE) trim else 0xffdce8ef.toInt()
            add(face(stripe,V(-2.25f,1.034f,z),V(-.8f,1.122f,z),V(-.8f,1.122f,z+.15f),V(-2.25f,1.034f,z+.15f)))
        }
    }
    private fun door(side: Float,level: Float): List<Face> {
        val angle=level.coerceIn(0f,1f)*1.05f
        fun v(x: Float,y: Float,z: Float): V {
            val dx=x+.72f; val dz=z-side*.96f
            return V(-.72f+dx*cos(angle)-side*dz*sin(angle),y,side*.96f+side*dx*sin(angle)+dz*cos(angle))
        }
        return listOf(
            face(bodyColor,v(-.72f,.4f,side*.97f),v(.83f,.4f,side*.97f),v(.83f,1.03f,side*.97f),v(-.72f,1.03f,side*.97f)),
            face(glass,v(-.69f,1.12f,side*.80f),v(-.22f,roofHeight-.035f,side*.70f),v(.55f,roofHeight-.035f,side*.70f),v(.55f,1.12f,side*.80f)),
            face(0xffbecbd8.toInt(),v(.50f,.94f,side*.98f),v(.72f,.94f,side*.98f),v(.72f,.965f,side*.98f),v(.50f,.965f,side*.98f)),
            face(trim,v(-.72f,1.02f,side*.97f),v(.83f,1.02f,side*.97f),v(.83f,1.12f,side*.8f),v(-.72f,1.12f,side*.8f)))
    }
    private fun wheels(): List<Face> = buildList {
        val theta=travelMeters/.34f
        for(x in listOf(-1.55f,1.55f)) for(side in listOf(-1f,1f)) {
            val z=side*1.01f
            for(i in 0 until 24) {
                val a=i*2f*PI.toFloat()/24; val b=(i+1)*2f*PI.toFloat()/24
                fun point(r: Float,angle: Float,depth: Float)=V(x+cos(angle)*r,.47f+sin(angle)*r,depth)
                add(face(0xff111820.toInt(),point(.47f,a,z-side*.17f),point(.47f,b,z-side*.17f),point(.47f,b,z),point(.47f,a,z)))
                add(face(0xff3a4653.toInt(),point(.47f,a,z),point(.47f,b,z),point(.35f,b,z+side*.005f),point(.35f,a,z+side*.005f)))
                add(face(0xffa4b5c6.toInt(),point(.35f,a,z+side*.01f),point(.35f,b,z+side*.01f),point(.31f,b,z+side*.01f),point(.31f,a,z+side*.01f)))
                add(face(trim,V(x,.47f,z+side*.012f),point(.31f,a,z+side*.012f),point(.31f,b,z+side*.012f)))
            }
            for(i in 0 until 5) {
                val a=i*2f*PI.toFloat()/5-theta; val b=a+.15f
                add(face(0xffd7e2ed.toInt(),V(x,.47f,z+side*.025f),V(x+cos(a)*.32f,.47f+sin(a)*.32f,z+side*.025f),V(x+cos(b)*.32f,.47f+sin(b)*.32f,z+side*.025f)))
            }
        }
    }
    override fun draw(canvas: Canvas) {
        val saved=canvas.save(); val scale=min(bounds.width()/900f,bounds.height()/510f)
        canvas.translate(bounds.left+(bounds.width()-900*scale)/2,bounds.top+(bounds.height()-510*scale)/2); canvas.scale(scale,scale)
        // A studio floor, not a navigation map. Movement advances only with a valid vehicle speed.
        paint.shader=null; paint.color=0x304a83ae; paint.strokeWidth=1f
        for(i in -5..7) {
            val x=i.toFloat()+(travelMeters%1f)
            val a=project(V(x,0f,-3f)); val b=project(V(x,0f,3f)); canvas.drawLine(a.x,a.y,b.x,b.y,paint)
        }
        paint.color=0x65000000; canvas.drawOval(115f,285f,810f,449f,paint)
        val faces=staticFaces+door(1f,leftDoor)+door(-1f,rightDoor)+wheels()+lamps()
        for(face in faces.sortedBy { f -> f.points.sumOf { depth(it).toDouble() }/f.points.size }) {
            path.reset()
            face.points.forEachIndexed { i,v -> val p=project(v); if(i==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y) }; path.close()
            paint.shader=null; paint.color=shade(face); canvas.drawPath(path,paint); canvas.drawPath(path,outline)
        }
        if(lights>.01f) for(side in listOf(-1f,1f)) {
            val p=project(V(-2.43f,.88f,side*.77f)); val radius=38f
            paint.shader=RadialGradient(p.x,p.y,radius,Color.argb((125*lights).toInt(),187,224,255),Color.TRANSPARENT,Shader.TileMode.CLAMP)
            canvas.drawCircle(p.x,p.y,radius,paint)
        }
        paint.shader=null; canvas.restoreToCount(saved)
    }
    private fun lamps(): List<Face> = buildList {
        val color=if(lights>.01f) 0xffe4f5ff.toInt() else 0xff657e91.toInt()
        for(side in listOf(-1f,1f)) {
            if(modern) for(i in 0..2) {
                val z=side*(.62f+i*.10f)
                add(face(color,V(-2.425f,.80f,z),V(-2.425f,.95f,z),V(-2.425f,.95f,z+side*.045f),V(-2.425f,.80f,z+side*.045f),emissive=true))
            } else {
                val z=side*.77f
                val points=(0..15).map { val a=it*2f*PI.toFloat()/16; V(-2.425f,.865f+sin(a)*.09f,z+cos(a)*.13f) }
                add(Face(points,color,true))
            }
        }
    }
    override fun setAlpha(alpha: Int)=Unit
    override fun setColorFilter(colorFilter: ColorFilter?)=Unit
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth()=900
    override fun getIntrinsicHeight()=510
}
