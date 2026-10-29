package com.mustangos.launcher.render

import com.mustangos.core.MustangBody
import com.mustangos.core.MustangGeneration
import com.mustangos.core.MustangProfile
import kotlin.math.*

/**
 * Procedural Mustang geometry in metres: x forward, y up, z towards the driver (left) side.
 * Pure Kotlin so the same mesh is tested on the JVM, previewed on a PC and uploaded to OpenGL ES on the unit.
 * Smooth surfaces come from analytic grids; small details (grille, lamps, panel gaps) are shaded per pixel.
 */
enum class Material(val id: Int) { PAINT(0), GLASS(1), TRIM(2), CHROME(3), RUBBER(4), RIM(5), BRAKE(6), CALIPER(7), FABRIC(8), INTERIOR(9), UNDER(10) }
enum class Part { BODY, DOOR_LEFT, DOOR_RIGHT, WHEEL, CALIPER }

class MeshPart(val part: Part,val material: Material,val positions: FloatArray,val normals: FloatArray,val indices: ShortArray) {
    val vertexCount get() = positions.size/3
}

/** Proportions per visual family. Values follow published exterior dimensions, rounded; not a CAD reproduction. */
data class BodySpec(
    val frontX: Float, val rearX: Float, val axle: Float, val halfWidth: Float, val track: Float,
    val tireR: Float, val tireW: Float, val rimR: Float, val bottom: Float,
    val beltFront: Float, val beltMid: Float, val beltRear: Float,
    val noseTop: Float, val cowlTop: Float, val deckTop: Float, val tailTop: Float,
    val cowlX: Float, val roofFrontX: Float, val roofRearX: Float, val deckX: Float, val roofY: Float,
    val glassBottom: Float, val glassTop: Float, val doorFrontX: Float, val doorRearX: Float,
    val noseRound: Float, val tailRound: Float, val noseLean: Float, val archGap: Float, val style: Int,
    val hoodBulge: Float=.022f, val haunch: Float=.025f
) {
    val length get() = frontX-rearX
    val archR get() = tireR+archGap
    companion object {
        fun of(profile: MustangProfile): BodySpec = when(MustangGeneration.forYear(profile.year)) {
            MustangGeneration.CLASSIC -> BodySpec(2.30f,-2.31f,1.37f,.87f,.73f,.325f,.19f,.19f,.20f,
                .84f,.86f,.88f,.80f,.90f,.90f,.86f,.55f,-.20f,-.85f,if(profile.body==MustangBody.FASTBACK) -1.95f else -1.45f,1.30f,
                .74f,.60f,.70f,-.42f,.30f,.20f,.0f,.075f,0)
            MustangGeneration.II -> BodySpec(2.20f,-2.25f,1.22f,.88f,.73f,.32f,.19f,.18f,.19f,
                .80f,.84f,.86f,.76f,.88f,.88f,.84f,.50f,-.25f,-.80f,-1.45f,1.27f,.74f,.60f,.62f,-.45f,.30f,.25f,.0f,.08f,0)
            MustangGeneration.FOX -> BodySpec(2.24f,-2.32f,1.28f,.865f,.74f,.32f,.21f,.20f,.18f,
                .80f,.85f,.87f,.74f,.90f,.90f,.86f,.50f,-.28f,-.95f,-1.60f,1.32f,.75f,.60f,.62f,-.48f,.22f,.18f,-.02f,.08f,1)
            MustangGeneration.SN95 -> BodySpec(2.26f,-2.39f,1.28f,.93f,.77f,.33f,.23f,.22f,.18f,
                .84f,.88f,.92f,.76f,.95f,.96f,.92f,.52f,-.24f,-.92f,-1.62f,1.35f,.77f,.61f,.66f,-.48f,.40f,.28f,.0f,.07f,2)
            MustangGeneration.S197 -> BodySpec(2.36f,-2.42f,1.36f,.94f,.79f,.345f,.235f,.23f,.17f,
                .86f,.92f,.99f,.80f,.99f,1.01f,.99f,.62f,-.18f,-.84f,-1.58f,1.39f,.78f,.60f,.78f,-.48f,.42f,.28f,.08f,.055f,3,.035f,.055f)
            MustangGeneration.S550 -> BodySpec(2.38f,-2.41f,1.36f,.958f,.80f,.35f,.255f,.24f,.16f,
                .84f,.90f,.96f,.76f,.97f,.99f,.97f,.66f,-.12f,-.78f,-1.70f,1.38f,.79f,.58f,.80f,-.46f,.50f,.30f,.03f,.05f,4,.03f,.04f)
            MustangGeneration.S650 -> BodySpec(2.40f,-2.41f,1.36f,.96f,.80f,.35f,.255f,.245f,.16f,
                .85f,.91f,.97f,.78f,.98f,1.0f,.98f,.66f,-.12f,-.78f,-1.70f,1.40f,.79f,.58f,.80f,-.46f,.46f,.30f,.04f,.05f,5,.032f,.045f)
        }
    }
}

private class Builder(val part: Part,val material: Material) {
    var pos=FloatArray(3*1024); var nrm=FloatArray(3*1024); var idx=ShortArray(4096)
    var vertices=0; var indexCount=0
    fun vertex(p: V,n: V): Int {
        if(vertices*3+3>pos.size) { pos=pos.copyOf(pos.size*2); nrm=nrm.copyOf(nrm.size*2) }
        val o=vertices*3
        pos[o]=p.x; pos[o+1]=p.y; pos[o+2]=p.z
        val l=n.length().takeIf { it>1e-6f } ?: 1f
        nrm[o]=n.x/l; nrm[o+1]=n.y/l; nrm[o+2]=n.z/l
        return vertices++
    }
    fun tri(a: Int,b: Int,c: Int) {
        if(indexCount+3>idx.size) idx=idx.copyOf(idx.size*2)
        idx[indexCount++]=a.toShort(); idx[indexCount++]=b.toShort(); idx[indexCount++]=c.toShort()
    }
    fun full(extra: Int)=vertices+extra>65000
    fun build()=MeshPart(part,material,pos.copyOf(vertices*3),nrm.copyOf(vertices*3),idx.copyOf(indexCount))
}

data class V(val x: Float,val y: Float,val z: Float) {
    operator fun plus(o: V)=V(x+o.x,y+o.y,z+o.z)
    operator fun minus(o: V)=V(x-o.x,y-o.y,z-o.z)
    operator fun times(s: Float)=V(x*s,y*s,z*s)
    fun cross(o: V)=V(y*o.z-z*o.y,z*o.x-x*o.z,x*o.y-y*o.x)
    fun dot(o: V)=x*o.x+y*o.y+z*o.z
    fun length()=sqrt(x*x+y*y+z*z)
    fun normalized()=this*(1f/length().coerceAtLeast(1e-6f))
    fun mirror()=V(x,y,-z)
}

/** Collects parts, splitting a builder before it exceeds 16-bit indices. */
private class MeshSet {
    val done=mutableListOf<MeshPart>()
    private val open=mutableMapOf<Pair<Part,Material>,Builder>()
    fun get(part: Part,material: Material,needed: Int): Builder {
        val key=part to material
        val current=open[key]
        if(current!=null && !current.full(needed)) return current
        current?.let { done.add(it.build()) }
        return Builder(part,material).also { open[key]=it }
    }
    fun finish(): List<MeshPart> { open.values.forEach { if(it.vertices>0) done.add(it.build()) }; open.clear(); return done }
}

/** Analytic grid with normals from central differences, emitted per cell with a part/material selector. */
private class Grid(val cols: Int,val rows: Int,val at: (Int,Int) -> V) {
    val p=Array(cols) { i -> Array(rows) { j -> at(i,j) } }
    /** Mirrored halves meet at z=0: keep their normals in that plane so the seam is invisible. */
    var symmetric=false
    fun normal(i: Int,j: Int,flip: Boolean): V {
        val a=p[min(i+1,cols-1)][j]-p[max(i-1,0)][j]
        val b=p[i][min(j+1,rows-1)]-p[i][max(j-1,0)]
        var n=a.cross(b)
        if(n.length()<1e-7f) {
            // Degenerate pole: borrow the neighbouring row.
            val jj=if(j==0) 1 else j-1
            val a2=p[min(i+1,cols-1)][jj]-p[max(i-1,0)][jj]; n=a2.cross(b)
        }
        if(symmetric && abs(p[i][j].z)<1e-4f) n=V(n.x,n.y,0f)
        return if(flip) n*-1f else n
    }
    fun emit(set: MeshSet,flip: Boolean,mirror: Boolean,select: (Int,Int) -> Pair<Part,Material>?) {
        for(i in 0 until cols-1) for(j in 0 until rows-1) {
            val sel=select(i,j) ?: continue
            val b=set.get(if(mirror) mirrorPart(sel.first) else sel.first,sel.second,4)
            fun v(ii: Int,jj: Int): Int {
                val pp=p[ii][jj]; val nn=normal(ii,jj,flip)
                return if(mirror) b.vertex(pp.mirror(),nn.mirror()) else b.vertex(pp,nn)
            }
            val a=v(i,j); val c=v(i+1,j); val d=v(i+1,j+1); val e=v(i,j+1)
            if(flip xor mirror) { b.tri(a,d,c); b.tri(a,e,d) } else { b.tri(a,c,d); b.tri(a,d,e) }
        }
    }
    companion object { fun mirrorPart(p: Part)=if(p==Part.DOOR_LEFT) Part.DOOR_RIGHT else p }
}

class MustangModel(val profile: MustangProfile) {
    val spec=BodySpec.of(profile)
    val convertible=profile.body==MustangBody.CONVERTIBLE
    val parts: List<MeshPart>
    val wheel: List<MeshPart>

    init {
        val set=MeshSet()
        body(set); greenhouse(set); interior(set); details(set)
        parts=set.finish()
        val wheels=MeshSet(); wheel(wheels); wheel=wheels.finish()
    }

    // ---- Profile curves ---------------------------------------------------------------------------------------
    private fun smooth(a: Float,b: Float,t: Float): Float { val k=((t-a)/(b-a)).coerceIn(0f,1f); return k*k*(3-2*k) }
    private fun lerp(a: Float,b: Float,t: Float)=a+(b-a)*t
    /** Plan-view rounding of the nose and tail: 1 along the body, falling to 0 at the bumper with a vertical tangent. */
    fun plan(x: Float): Float = with(spec) {
        val front=frontX-noseRound; val rear=rearX+tailRound
        when {
            // Exact ends: float rounding in the curve below would otherwise leave a centre slit.
            x>=frontX-1e-4f || x<=rearX+1e-4f -> 0f
            x>front -> (1-((x-front)/noseRound).coerceIn(0f,1f).pow(2.6f)).coerceAtLeast(0f).pow(1/2.6f)
            x<rear -> (1-((rear-x)/tailRound).coerceIn(0f,1f).pow(3.2f)).coerceAtLeast(0f).pow(1/3.2f)
            else -> 1f
        }
    }
    fun halfWidth(x: Float): Float = with(spec) {
        // Front fender and rear haunch are slightly fuller than the door.
        val waist=1f-.012f*exp(-((x-(doorFrontX+doorRearX)/2)/.7f).pow(2))
        val hips=1f+haunch*exp(-((x-(-axle))/.55f).pow(2))*.6f
        halfWidth*waist*hips/(1f+haunch*.6f)*plan(x)
    }
    fun bottom(x: Float): Float = with(spec) {
        val front=smooth(axle+.35f,frontX,x)*.07f; val rear=smooth(-axle-.35f,rearX,x)*.10f
        bottom+front+rear
    }
    fun belt(x: Float): Float = with(spec) {
        if(x>0) lerp(beltMid,beltFront,smooth(doorFrontX-.2f,frontX-.1f,x))
        else lerp(beltMid,beltRear,smooth(doorRearX+.1f,rearX+.6f,x))
    }
    /** Centre-line height of hood, cowl, cabin floor line and deck. */
    fun top(x: Float): Float = with(spec) {
        when {
            x>=cowlX -> lerp(cowlTop,noseTop,smooth(cowlX,frontX,x).pow(1.25f))
            x<=deckX -> lerp(deckTop,tailTop,smooth(deckX,rearX,x))
            else -> lerp(deckTop,cowlTop,smooth(deckX,cowlX,x))
        }
    }
    /** Body top skin height across the section: shoulder crown rising to the centre line, plus the hood dome. */
    fun topHeight(x: Float,z: Float): Float = with(spec) {
        val w=halfWidth(x); val ybelt=belt(x)
        val edge=(w*(1-.045f)-.03f).coerceAtLeast(1e-3f)
        val t=(1-z/edge).coerceIn(0f,1f)
        val crown=1-(1-t).pow(2.2f)
        var y=lerp(ybelt+.01f,max(top(x),ybelt+.012f),crown)
        if(x>cowlX) y+=hoodBulge*smooth(.40f,.30f,z)*smooth(cowlX,cowlX+.20f,x)*(1-smooth(frontX-.45f,frontX-.05f,x))
        y
    }
    fun archTop(x: Float): Float? = with(spec) {
        for(axleX in floatArrayOf(axle,-axle)) {
            val dx=x-axleX
            if(abs(dx)<archR) return tireR+sqrt(archR*archR-dx*dx)
        }
        null
    }
    /** Glass/greenhouse silhouette in side view. */
    fun roofLine(x: Float): Float = with(spec) {
        val cowl=top(cowlX)+.01f
        when {
            x>=roofFrontX -> { val t=((x-roofFrontX)/(cowlX-roofFrontX)).coerceIn(0f,1f); lerp(roofY,cowl,t.pow(1.15f))-.035f*sin(PI.toFloat()*t) }
            x>=roofRearX -> roofY+.012f*sin(PI.toFloat()*(x-roofRearX)/(roofFrontX-roofRearX))
            else -> {
                val t=((roofRearX-x)/(roofRearX-deckX)).coerceIn(0f,1f)
                val end=top(deckX)+.005f
                if(convertible) lerp(roofY-.015f,end,t.pow(1.9f))+.02f*sin(PI.toFloat()*t) else lerp(roofY,end,t.pow(1.25f))-.05f*sin(PI.toFloat()*t)
            }
        }
    }
    fun glassHalf(x: Float,atTop: Boolean): Float = with(spec) {
        val base=if(atTop) glassTop else glassBottom
        val taper=smooth(roofFrontX,cowlX+.05f,x)*(if(atTop) .015f else .0f)+smooth(roofRearX,deckX,x)*(if(atTop) .02f else .06f)
        base-taper
    }

    // ---- Body ---------------------------------------------------------------------------------------------------
    private fun stations(): FloatArray = with(spec) {
        val xs=sortedSetOf<Float>()
        val n=110
        for(i in 0..n) xs.add(lerp(rearX,frontX,i.toFloat()/n))
        // Dense samples where the plan rounding and the arches change quickly.
        for(i in 1..24) { val t=i/25f; xs.add(frontX-noseRound*(1-t*t)); xs.add(rearX+tailRound*(1-t*t)) }
        xs.add(frontX-.004f); xs.add(rearX+.004f)
        for(axleX in floatArrayOf(axle,-axle)) for(i in -50..50) xs.add(axleX+archR*i/50f*1.04f)
        for(x in floatArrayOf(doorFrontX,doorRearX,cowlX,deckX)) xs.add(x)
        xs.toFloatArray()
    }
    private val rowsBottom=6
    /** Half-section rows from the bottom centre (j=0) around the side to the top centre (last). */
    private fun section(x: Float,j: Int,rows: Int): V = with(spec) {
        val w=halfWidth(x); val yb=bottom(x); val ybelt=belt(x); val yt=top(x)
        val side=floatArrayOf(.0f,.06f,.13f,.22f,.32f,.42f,.52f,.60f,.67f,.74f,.80f,.86f,.91f,.955f,.985f)
        val topRows=rows-rowsBottom-side.size-3
        val point: V=when {
            j<rowsBottom -> { val t=j/(rowsBottom-1f); V(x,yb,w*.90f*t) }
            j<rowsBottom+side.size -> {
                val k=side[j-rowsBottom]
                val y=lerp(yb+.035f,ybelt-.02f,k)
                // Rocker tuck, full side, character crease and tumblehome towards the shoulder.
                val tuck=1f-.075f*(1-smooth(0f,.34f,k)).pow(1.3f)
                val crease=.008f*exp(-((k-.72f)/.045f).pow(2))
                val tumble=.055f*smooth(.74f,1f,k).pow(1.3f)
                V(x,y,w*(tuck+crease-tumble))
            }
            j<rowsBottom+side.size+3 -> {
                // Shoulder radius between the side and the top skin.
                val a=(j-rowsBottom-side.size+1)/4f*(PI.toFloat()/2)
                val r=.03f
                V(x,ybelt-.02f+r*sin(a),w*(1-.045f)-r*(1-cos(a)))
            }
            else -> {
                val t=(j-rowsBottom-side.size-3+1f)/topRows
                val edge=w*(1-.045f)-.03f
                val z=edge*(1-t)
                V(x,topHeight(x,z),z)
            }
        }
        var p=point
        // Wheel openings: the lower skin ends on the arch, the underside becomes the wheel well.
        val arch=archTop(x)
        val lowLimit=ybelt-.12f
        if(arch!=null && p.y<lowLimit && j<rowsBottom+side.size) {
            val t=((p.y-yb)/(lowLimit-yb)).coerceIn(0f,1f)
            val y=lerp(arch,lowLimit,t)
            val lip=1f+.008f*exp(-(y-arch)/.03f)
            p=V(p.x,y,if(j<rowsBottom) p.z*(.82f+.18f*t) else p.z*lip)
        }
        // Shark nose: the upper fascia leans forward, the chin tucks back; the tail is near vertical.
        val nose=smooth(frontX-noseRound,frontX,x)
        if(nose>0) p=V(p.x+nose*(noseLean*smooth(yb+.2f,yt,p.y)-.07f*(1-smooth(yb,yb+.25f,p.y))),p.y,p.z)
        val tail=smooth(rearX+tailRound,rearX,x)
        if(tail>0) p=V(p.x+tail*.06f*(1-smooth(yb,yb+.3f,p.y)),p.y,p.z)
        return p
    }
    private fun body(set: MeshSet) = with(spec) {
        val xs=stations(); val rows=rowsBottom+15+3+18
        val grid=Grid(xs.size,rows) { i,j -> section(xs[i],j,rows) }.apply { symmetric=true }
        val sill=rowsBottom+2; val shoulderEnd=rowsBottom+15+3
        grid.emit(set,flip=false,mirror=false) { i,j -> select(grid,xs,i,j,sill,shoulderEnd) }
        grid.emit(set,flip=false,mirror=true) { i,j -> select(grid,xs,i,j,sill,shoulderEnd) }
        doorThickness(set,grid,xs,sill,shoulderEnd)
        wheelHouses(set)
    }
    /** Dark liners behind the tyres so the arches never show through the car. */
    private fun wheelHouses(set: MeshSet) = with(spec) {
        for(axleX in floatArrayOf(axle,-axle)) {
            val r=archR-.004f; val zIn=track-tireW*.75f; val zOut=halfWidth(axleX)*.985f
            val seg=36
            val shell=Grid(seg+1,2) { i,j ->
                val a=-.25f+(PI.toFloat()+.5f)*i/seg
                V(axleX+r*cos(a),tireR+r*sin(a),if(j==0) zIn else zOut)
            }
            shell.emit(set,true,false) { _,_ -> Part.BODY to Material.UNDER }
            shell.emit(set,true,true) { _,_ -> Part.BODY to Material.UNDER }
            val wall=Grid(seg+1,2) { i,j ->
                val a=-.25f+(PI.toFloat()+.5f)*i/seg
                val rr=if(j==0) 0f else r
                V(axleX+rr*cos(a),max(tireR+rr*sin(a),bottom(axleX)),zIn)
            }
            wall.emit(set,false,false) { _,_ -> Part.BODY to Material.UNDER }
            wall.emit(set,false,true) { _,_ -> Part.BODY to Material.UNDER }
        }
    }
    private fun select(grid: Grid,xs: FloatArray,i: Int,j: Int,sill: Int,shoulderEnd: Int): Pair<Part,Material>? = with(spec) {
        val x0=xs[i]; val x1=xs[i+1]; val xc=(x0+x1)/2
        val c=grid.p[i][j]; val zc=(c.z+grid.p[i][j+1].z)/2
        val inDoor=x0>=doorRearX-1e-4f && x1<=doorFrontX+1e-4f
        if(j<rowsBottom) return Part.BODY to Material.UNDER
        val cabin=xc>deckX+.02f && xc<cowlX-.02f
        if(j>=shoulderEnd-1 && cabin && zc<glassHalf(xc,false)-.01f) return null
        if(inDoor && j>=sill && (j<shoulderEnd-1 || zc>=glassHalf(xc,false)-.01f)) return Part.DOOR_LEFT to Material.PAINT
        if(j<rowsBottom+2) return Part.BODY to if(archTop(xc)!=null) Material.UNDER else Material.PAINT
        return Part.BODY to Material.PAINT
    }
    /** Inner door skin and edges so an open door reads as a solid panel. */
    private fun doorThickness(set: MeshSet,grid: Grid,xs: FloatArray,sill: Int,shoulderEnd: Int) = with(spec) {
        val cols=xs.indices.filter { xs[it]>=doorRearX-1e-4f && xs[it]<=doorFrontX+1e-4f }
        val i0=cols.first(); val i1=cols.last()
        val topRow=shoulderEnd-2
        val depth=.075f
        fun inner(p: V)=V(p.x,p.y,p.z-depth)
        for(mirror in listOf(false,true)) {
            val part=if(mirror) Part.DOOR_RIGHT else Part.DOOR_LEFT
            val b=set.get(part,Material.INTERIOR,(i1-i0+1)*(topRow-sill+1)*2+64)
            fun add(p: V,n: V)=if(mirror) b.vertex(p.mirror(),n.mirror()) else b.vertex(p,n)
            fun quad(a: Int,c: Int,d: Int,e: Int) { if(mirror) { b.tri(a,d,c); b.tri(a,e,d) } else { b.tri(a,c,d); b.tri(a,d,e) } }
            val idx=Array(i1-i0+1) { IntArray(topRow-sill+1) }
            for(i in i0..i1) for(j in sill..topRow) idx[i-i0][j-sill]=add(inner(grid.p[i][j]),V(0f,0f,-1f))
            for(i in 0 until i1-i0) for(j in 0 until topRow-sill) quad(idx[i][j],idx[i][j+1],idx[i+1][j+1],idx[i+1][j])
            // Edges: rear, bottom, top and front.
            fun edge(a: V,c: V) {
                val n=(c-a).cross(V(0f,0f,1f)).normalized()
                val v0=add(a,n); val v1=add(c,n); val v2=add(inner(c),n); val v3=add(inner(a),n)
                quad(v0,v1,v2,v3)
            }
            for(j in sill until topRow) { edge(grid.p[i0][j+1],grid.p[i0][j]); edge(grid.p[i1][j],grid.p[i1][j+1]) }
            for(i in i0 until i1) { edge(grid.p[i][sill],grid.p[i+1][sill]); edge(grid.p[i+1][topRow],grid.p[i][topRow]) }
        }
    }

    // ---- Greenhouse -----------------------------------------------------------------------------------------------
    private fun greenhouse(set: MeshSet) = with(spec) {
        val n=72; val rows=22
        val xs=FloatArray(n+1) { lerp(deckX,cowlX,it.toFloat()/n) }.toSortedSet().apply { add(roofFrontX); add(roofRearX); add(doorRearX) }.toFloatArray()
        val sideRows=9
        fun at(i: Int,j: Int): V {
            val x=xs[i]; val wb=glassHalf(x,false); val wt=glassHalf(x,true)
            val yb=topHeight(x,wb); val yt=max(roofLine(x),yb)
            val r=.045f*smooth(0f,.25f,yt-yb)
            return if(j<=sideRows) {
                val t=j/sideRows.toFloat()
                val y=lerp(yb,yt-r,t)
                V(x,y,lerp(wb,wt+.012f,t)+.018f*sin(PI.toFloat()*t))
            } else if(j<=sideRows+4) {
                val a=(j-sideRows)/4f*(PI.toFloat()/2)
                V(x,yt-r+r*sin(a),wt+.012f-r*(1-cos(a)))
            } else {
                val t=(j-sideRows-4f)/(rows-1-sideRows-4)
                val edge=wt+.012f-r
                V(x,yt+.022f*(1-(1-t).pow(2))*smooth(.02f,.2f,yt-yb),edge*(1-t))
            }
        }
        val grid=Grid(xs.size,rows,::at).apply { symmetric=true }
        val glassTopRow=sideRows-1
        val sel: (Int,Int) -> Pair<Part,Material>? = { i,j ->
            val xc=(xs[i]+xs[i+1])/2
            val roofMat=if(convertible) Material.FABRIC else Material.PAINT
            val windshield=xc>roofFrontX+.02f; val rearGlass=xc<roofRearX-.02f
            when {
                j>=sideRows+4 -> when {
                    windshield -> if(j>=sideRows+4 && xc<cowlX-.03f && xc>roofFrontX+.07f) Part.BODY to Material.GLASS else Part.BODY to Material.PAINT
                    rearGlass -> if(convertible) { if(j>=sideRows+8 && xc>deckX+.25f && xc<roofRearX-.12f) Part.BODY to Material.GLASS else Part.BODY to roofMat }
                        else if(j>=sideRows+6 && xc>deckX+.06f) Part.BODY to Material.GLASS else Part.BODY to Material.PAINT
                    else -> Part.BODY to roofMat
                }
                j>=sideRows -> Part.BODY to if(windshield || !convertible) Material.PAINT else roofMat
                else -> {
                    val doorGlass=xc>doorRearX+.025f && xc<cowlX-.05f && j in 1..glassTopRow
                    val quarter=xc<doorRearX-.025f && xc>(if(convertible) doorRearX-.45f else roofRearX+.02f) && j in 1..glassTopRow-1
                    val bPillar=abs(xc-doorRearX)<=.025f && j in 1..glassTopRow
                    when {
                        doorGlass -> Part.DOOR_LEFT to Material.GLASS
                        j==0 && xc>doorRearX && xc<doorFrontX -> Part.DOOR_LEFT to Material.TRIM
                        quarter -> Part.BODY to Material.GLASS
                        bPillar || j==0 -> Part.BODY to Material.TRIM
                        windshield -> Part.BODY to Material.PAINT
                        else -> Part.BODY to if(convertible) roofMat else Material.PAINT
                    }
                }
            }
        }
        grid.emit(set,flip=true,mirror=false,sel); grid.emit(set,flip=true,mirror=true,sel)
    }

    // ---- Interior -------------------------------------------------------------------------------------------------
    private fun box(set: MeshSet,part: Part,material: Material,c: V,size: V,round: Float=.35f,tiltZ: Float=0f,segments: Int=10) {
        // Superellipsoid "pillow" box: soft upholstered volumes with smooth normals.
        val b=set.get(part,material,(segments+1)*(segments*2+1))
        val e=round
        fun f(a: Float,p: Float)=sign(a)*abs(a).pow(p)
        val rows=segments; val cols=segments*2
        val grid=Grid(cols+1,rows+1) { i,j ->
            val u=-PI.toFloat()+2*PI.toFloat()*i/cols; val v=-PI.toFloat()/2+PI.toFloat()*j/rows
            val x=size.x*f(cos(v),e)*f(cos(u),e); val y=size.y*f(sin(v),e); val z=size.z*f(cos(v),e)*f(sin(u),e)
            val ct=cos(tiltZ); val st=sin(tiltZ)
            V(c.x+x*ct-y*st,c.y+x*st+y*ct,c.z+z)
        }
        val ids=Array(cols+1) { i -> IntArray(rows+1) { j -> b.vertex(grid.p[i][j],grid.normal(i,j,true)) } }
        for(i in 0 until cols) for(j in 0 until rows) { b.tri(ids[i][j],ids[i+1][j],ids[i+1][j+1]); b.tri(ids[i][j],ids[i+1][j+1],ids[i][j+1]) }
    }
    private fun interior(set: MeshSet) = with(spec) {
        val floorY=bottom(0f)+.14f
        box(set,Part.BODY,Material.INTERIOR,V((cowlX+deckX)/2,floorY,0f),V((cowlX-deckX)/2,.03f,glassBottom),.2f)
        for(side in floatArrayOf(1f,-1f)) {
            val z=side*.37f
            box(set,Part.BODY,Material.INTERIOR,V(doorRearX+.32f,floorY+.20f,z),V(.26f,.07f,.24f),.45f,.10f)
            box(set,Part.BODY,Material.INTERIOR,V(doorRearX+.04f,floorY+.53f,z),V(.07f,.33f,.23f),.45f,-.25f)
            box(set,Part.BODY,Material.INTERIOR,V(doorRearX-.07f,floorY+.92f,z),V(.06f,.09f,.13f),.5f,-.25f)
            box(set,Part.BODY,Material.INTERIOR,V(doorRearX-.55f,floorY+.38f,z*1.05f),V(.10f,.25f,.30f),.5f,-.32f)
        }
        box(set,Part.BODY,Material.INTERIOR,V(cowlX-.18f,belt(cowlX)-.06f,0f),V(.22f,.10f,glassBottom-.04f),.3f)
        box(set,Part.BODY,Material.INTERIOR,V(doorRearX+.55f,floorY+.20f,0f),V(.45f,.09f,.09f),.4f)
        // Steering wheel on the driver's (left, +z) side.
        val b=set.get(Part.BODY,Material.INTERIOR,40*12)
        val centre=V(cowlX-.42f,belt(0f)-.05f,.37f); val tilt=.42f
        val ids=Array(41) { IntArray(9) }
        for(i in 0..40) for(j in 0..8) {
            val u=2*PI.toFloat()*i/40; val v=2*PI.toFloat()*j/8
            val r=.18f+.018f*cos(v)
            val local=V(.018f*sin(v),r*sin(u),r*cos(u))
            val n=V(sin(v),cos(v)*sin(u),cos(v)*cos(u))
            fun rot(p: V)=V(p.x*cos(tilt)-p.y*sin(tilt),p.x*sin(tilt)+p.y*cos(tilt),p.z)
            ids[i][j]=b.vertex(centre+rot(local),rot(n))
        }
        for(i in 0 until 40) for(j in 0 until 8) { b.tri(ids[i][j],ids[i+1][j],ids[i+1][j+1]); b.tri(ids[i][j],ids[i+1][j+1],ids[i][j+1]) }
    }

    // ---- Mirrors, handles, exhaust -------------------------------------------------------------------------------
    private fun details(set: MeshSet) = with(spec) {
        for(mirror in listOf(false,true)) {
            val s=if(mirror) -1f else 1f
            val part=if(mirror) Part.DOOR_RIGHT else Part.DOOR_LEFT
            val mx=cowlX-.13f; val my=belt(mx)+.075f; val mz=halfWidth(mx)*.93f+.075f
            box(set,part,Material.PAINT,V(mx,my,s*mz),V(.055f,.05f,.085f),.55f)
            box(set,part,Material.TRIM,V(mx+.01f,my-.045f,s*(mz-.07f)),V(.04f,.018f,.05f),.5f)
            val hx=doorRearX+.17f; val hy=belt(hx)-.10f
            box(set,part,Material.CHROME,V(hx,hy,s*(halfWidth(hx)*.985f)),V(.075f,.012f,.012f),.5f,0f,8)
        }
        for(s in floatArrayOf(1f,-1f)) {
            val z=s*(halfWidth(rearX+.3f)-.30f)
            val b=set.get(Part.BODY,Material.CHROME,40*6)
            val ids=Array(21) { IntArray(6) }
            for(i in 0..20) for(j in 0..5) {
                val a=2*PI.toFloat()*i/20; val x=rearX+.10f-j*.032f
                val r=if(j==0) .035f else .045f
                ids[i][j]=b.vertex(V(x,bottom(rearX)+.10f+r*sin(a),z+r*cos(a)),V(0f,sin(a),cos(a)))
            }
            for(i in 0 until 20) for(j in 0 until 5) { b.tri(ids[i][j],ids[i+1][j],ids[i+1][j+1]); b.tri(ids[i][j],ids[i+1][j+1],ids[i][j+1]) }
        }
    }

    // ---- Wheel (local: axle along z, outer face at +z) ------------------------------------------------------------
    private fun wheel(set: MeshSet) = with(spec) {
        val seg=72
        fun revolve(part: Part,material: Material,profile: List<Pair<Float,Float>>,flip: Boolean) {
            val grid=Grid(seg+1,profile.size) { i,j ->
                val a=2*PI.toFloat()*i/seg; val (r,z)=profile[j]
                V(r*cos(a),r*sin(a),z)
            }
            grid.emit(set,flip,false) { _,_ -> part to material }
        }
        val w=tireW/2; val R=tireR
        val tire=buildList {
            for(k in 0..6) { val a=PI.toFloat()/2*k/6; add(rimR+.012f+(R-rimR-.02f)*(.55f+.45f*sin(a))+.01f*sin(a) to w*(.80f+.20f*cos(a))) }
            for(k in 0..10) add(R-(.004f*sin(PI.toFloat()*k/10)) to lerp(w*.95f,-w*.95f,k/10f))
            for(k in 0..6) { val a=PI.toFloat()/2*(6-k)/6; add(rimR+.012f+(R-rimR-.02f)*(.55f+.45f*sin(a))+.01f*sin(a) to -w*(.80f+.20f*cos(a))) }
        }
        // Profile order runs outer sidewall -> tread -> inner sidewall.
        revolve(Part.WHEEL,Material.RUBBER,tire,false)
        val lip=w*.82f
        revolve(Part.WHEEL,Material.RIM,listOf(rimR+.016f to lip-.012f,rimR+.019f to lip,rimR+.012f to lip+.01f,rimR to lip+.006f,rimR-.008f to lip-.01f),true)
        revolve(Part.WHEEL,Material.UNDER,listOf(rimR-.008f to lip-.012f,rimR-.01f to -w*.7f,.04f to -w*.7f),true)
        revolve(Part.WHEEL,Material.BRAKE,listOf(rimR*.80f to -w*.05f,rimR*.80f to -w*.25f,rimR*.30f to -w*.25f,rimR*.30f to -w*.05f,rimR*.80f to -w*.05f),true)
        // Ten split spokes with a concave dish towards the hub.
        val spokes=5
        for(k in 0 until spokes) for(split in floatArrayOf(-1f,1f)) {
            val b=set.get(Part.WHEEL,Material.RIM,200)
            val base=2*PI.toFloat()*k/spokes+split*.105f
            val steps=8
            val ids=Array(steps+1) { IntArray(6) }
            for(s in 0..steps) {
                val t=s/steps.toFloat()
                val r=lerp(rimR*.30f,rimR-.004f,t)
                val half=lerp(.020f,.013f,t)
                val bend=split*.06f*t*t
                val a=base+bend
                val zFace=lerp(lip-.045f,lip-.004f,t.pow(.8f))
                val dir=V(cos(a),sin(a),0f); val side=V(-sin(a),cos(a),0f)
                val ridge=.010f
                val pts=listOf(side*(-half)+V(0f,0f,zFace-.03f),side*(-half)+V(0f,0f,zFace-.004f),side*(-half*.35f)+V(0f,0f,zFace+ridge*.2f),
                    side*(half*.35f)+V(0f,0f,zFace+ridge*.2f),side*half+V(0f,0f,zFace-.004f),side*half+V(0f,0f,zFace-.03f))
                val normals=listOf(side*-1f,(side*-1f+V(0f,0f,.7f)),V(0f,0f,1f)+side*-.15f,V(0f,0f,1f)+side*.15f,side+V(0f,0f,.7f),side)
                for(q in 0..5) ids[s][q]=b.vertex(dir*r+pts[q],normals[q])
            }
            for(s in 0 until steps) for(q in 0 until 5) { b.tri(ids[s][q],ids[s+1][q],ids[s+1][q+1]); b.tri(ids[s][q],ids[s+1][q+1],ids[s][q+1]) }
        }
        revolve(Part.WHEEL,Material.RIM,listOf(rimR*.33f to lip-.06f,rimR*.33f to lip-.035f,rimR*.25f to lip-.03f,.0f to lip-.025f),true)
        revolve(Part.WHEEL,Material.CHROME,listOf(.045f to lip-.027f,.03f to lip-.019f,.0f to lip-.017f),true)
        for(k in 0 until spokes) {
            val a=2*PI.toFloat()*(k+.5f)/spokes
            val c=V(cos(a)*rimR*.22f,sin(a)*rimR*.22f,0f)
            val b=set.get(Part.WHEEL,Material.CHROME,7*3)
            val ids=Array(7) { IntArray(3) }
            for(i in 0..6) for(j in 0..2) {
                val u=2*PI.toFloat()*i/6
                ids[i][j]=b.vertex(c+V(cos(u)*.011f*(if(j==2) .5f else 1f),sin(u)*.011f*(if(j==2) .5f else 1f),lip-.04f+j*.008f),V(cos(u),sin(u),if(j==2) 1f else .2f))
            }
            for(i in 0 until 6) for(j in 0 until 2) { b.tri(ids[i][j],ids[i+1][j],ids[i+1][j+1]); b.tri(ids[i][j],ids[i+1][j+1],ids[i][j+1]) }
        }
        // Caliper: static, at the trailing upper side of the disc.
        box(set,Part.CALIPER,Material.CALIPER,V(cos(2.45f)*rimR*.66f,sin(2.45f)*rimR*.66f,-w*.02f),V(.05f,.07f,.035f),.45f,2.45f-PI.toFloat()/2)
    }
}
