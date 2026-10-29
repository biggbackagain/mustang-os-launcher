package com.mustangos.launcher.render

import com.mustangos.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class MustangModelTest {
    @Test fun everyFamilyBuildsFiniteIndexedMeshesWithinTheBodyEnvelope() {
        for(year in listOf(1965,1977,1990,1999,2010,2020,2024)) for(body in MustangBody.entries) {
            val scene=MustangScene(MustangProfile(year=year,body=body,version=MustangVersion.GT))
            val spec=scene.model.spec
            assertTrue(scene.meshes.isNotEmpty())
            for(mesh in scene.meshes) {
                assertTrue(mesh.vertexCount in 1..65000)
                assertTrue(mesh.indices.all { (it.toInt() and 0xffff)<mesh.vertexCount })
                assertTrue(mesh.positions.all { it.isFinite() }); assertTrue(mesh.normals.all { it.isFinite() })
            }
            for(mesh in scene.model.parts) for(i in 0 until mesh.vertexCount) {
                val x=mesh.positions[i*3]; val y=mesh.positions[i*3+1]; val z=mesh.positions[i*3+2]
                assertTrue("$year $body x=$x",x in spec.rearX-.12f..spec.frontX+.12f)
                assertTrue("$year $body y=$y",y in 0f..spec.roofY+.12f)
                assertTrue("$year $body z=$z",kotlin.math.abs(z)<=spec.halfWidth+.25f)
            }
            assertTrue(scene.model.parts.any { it.part==Part.DOOR_LEFT } && scene.model.parts.any { it.part==Part.DOOR_RIGHT })
        }
    }
    @Test fun doorsWheelsAndLightsChangeTheFrameIndependently() {
        val scene=MustangScene(MustangProfile.DEFAULT)
        val closed=scene.frame(SceneState(),900,510)
        val open=scene.frame(SceneState(leftDoor=1f),900,510)
        val leftIndex=scene.model.parts.indexOfFirst { it.part==Part.DOOR_LEFT }
        val rightIndex=scene.model.parts.indexOfFirst { it.part==Part.DOOR_RIGHT }
        fun matrix(frame: Frame,mesh: Int)=frame.calls.first { it.mesh==mesh }.model
        assertFalse(matrix(closed,leftIndex).contentEquals(matrix(open,leftIndex)))
        assertTrue(matrix(closed,rightIndex).contentEquals(matrix(open,rightIndex)))
        val rolled=scene.frame(SceneState(travelMeters=.5f),900,510)
        val wheelMesh=scene.model.parts.size
        assertFalse(matrix(closed,wheelMesh).contentEquals(matrix(rolled,wheelMesh)))
        assertTrue(closed.glows.isEmpty()); assertEquals(2,scene.frame(SceneState(lights=1f),900,510).glows.size); assertEquals(2,scene.frame(SceneState(lights=1f,yaw=2.6f),900,510).glows.size)
        assertTrue(closed.calls.last().transparent)
    }
    /** Writes the scene for the WebGL development preview (build/render-preview). Not a device artifact. */
    @Test fun exportPreviewScene() {
        val out=File("build/render-preview"); out.mkdirs()
        fun b64(f: FloatArray)=Base64.getEncoder().encodeToString(ByteBuffer.allocate(f.size*4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(f) }.array())
        fun b64(s: ShortArray)=Base64.getEncoder().encodeToString(ByteBuffer.allocate(s.size*2).order(ByteOrder.LITTLE_ENDIAN).apply { asShortBuffer().put(s) }.array())
        fun q(s: String)="\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""
        fun arr(f: FloatArray)=f.joinToString(",","[","]")
        val profiles=mapOf("s197" to MustangProfile.DEFAULT,"s197coupe" to MustangProfile.DEFAULT.copy(body=MustangBody.COUPE,paint=MustangPaint.BLUE),
            "s550" to MustangProfile(2020,MustangVersion.GT,MustangBody.FASTBACK,MustangPaint.WHITE),"s650" to MustangProfile(2024,MustangVersion.DARK_HORSE,MustangBody.FASTBACK,MustangPaint.GRAY),
            "classic" to MustangProfile(1965,MustangVersion.GT,MustangBody.FASTBACK,MustangPaint.RED),"fox" to MustangProfile(1990,MustangVersion.GT,MustangBody.COUPE,MustangPaint.BLACK),
            "sn95" to MustangProfile(1999,MustangVersion.GT,MustangBody.COUPE,MustangPaint.YELLOW))
        val states=mapOf("closed" to SceneState(),"left" to SceneState(leftDoor=1f,yaw=1.05f),"right" to SceneState(rightDoor=1f,yaw=-1.0f),
            "lights" to SceneState(lights=1f),"rear" to SceneState(yaw=2.5f,lights=1f),"side" to SceneState(yaw=1.5708f,pitch=.08f))
        for((name,profile) in profiles) {
            val scene=MustangScene(profile)
            val json=StringBuilder("{")
            json.append("\"shaders\":{\"carV\":${q(MustangShaders.CAR_VERTEX)},\"carF\":${q(MustangShaders.CAR_FRAGMENT)},\"floorV\":${q(MustangShaders.FLOOR_VERTEX)},\"floorF\":${q(MustangShaders.FLOOR_FRAGMENT)},\"glowV\":${q(MustangShaders.GLOW_VERTEX)},\"glowF\":${q(MustangShaders.GLOW_FRAGMENT)}},")
            json.append("\"uniforms\":{\"bodyX\":${arr(scene.bodyX)},\"bodyY\":${arr(scene.bodyY)},\"door\":${arr(scene.door)},\"style\":${scene.style},\"car\":${arr(scene.floorCar)},\"track\":${arr(scene.floorTrack)}},")
            json.append("\"meshes\":[")
            scene.meshes.forEachIndexed { i,m -> if(i>0) json.append(','); json.append("{\"p\":${q(b64(m.positions))},\"n\":${q(b64(m.normals))},\"i\":${q(b64(m.indices))},\"mat\":${m.material.id}}") }
            json.append("],\"frames\":{")
            states.entries.forEachIndexed { k,(stateName,state) ->
                val f=scene.frame(state,900,510)
                if(k>0) json.append(',')
                json.append("${q(stateName)}:{\"vp\":${arr(f.viewProj)},\"eye\":[${f.eye.x},${f.eye.y},${f.eye.z}],\"right\":[${f.right.x},${f.right.y},${f.right.z}],\"up\":[${f.up.x},${f.up.y},${f.up.z}],\"lights\":${state.lights},\"travel\":${state.travelMeters},")
                json.append("\"calls\":[${f.calls.joinToString(",") { "{\"m\":${it.mesh},\"model\":${arr(it.model)},\"mat\":${it.material.id},\"base\":${arr(it.base)},\"t\":${it.transparent}}" }}],")
                json.append("\"glows\":[${f.glows.joinToString(",") { "{\"c\":[${it.center.x},${it.center.y},${it.center.z}],\"color\":${arr(it.color)},\"s\":${it.size}}" }}]}")
            }
            json.append("}}")
            File(out,"$name.json").writeText(json.toString())
        }
    }
}
