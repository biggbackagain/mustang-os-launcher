package com.mustangos.launcher.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** OpenGL ES 2.0 executor for [MustangScene] frames. Must be used on the thread that owns the EGL context. */
class MustangRenderer(private val scene: MustangScene) {
    private class Program(val id: Int) {
        private val cache=HashMap<String,Int>()
        fun u(name: String)=cache.getOrPut(name) { GLES20.glGetUniformLocation(id,name) }
    }
    private class Mesh(val positions: Int,val normals: Int,val indices: Int,val count: Int)
    private lateinit var car: Program
    private lateinit var floor: Program
    private lateinit var glow: Program
    private val meshes=ArrayList<Mesh>()
    private var floorQuad=0
    private var glowQuad=0
    var width=1; private set
    var height=1; private set

    fun init() {
        car=Program(program(MustangShaders.CAR_VERTEX,MustangShaders.CAR_FRAGMENT))
        floor=Program(program(MustangShaders.FLOOR_VERTEX,MustangShaders.FLOOR_FRAGMENT))
        glow=Program(program(MustangShaders.GLOW_VERTEX,MustangShaders.GLOW_FRAGMENT))
        for(part in scene.meshes) {
            val ids=IntArray(3); GLES20.glGenBuffers(3,ids,0)
            upload(GLES20.GL_ARRAY_BUFFER,ids[0],floats(part.positions))
            upload(GLES20.GL_ARRAY_BUFFER,ids[1],floats(part.normals))
            upload(GLES20.GL_ELEMENT_ARRAY_BUFFER,ids[2],ByteBuffer.allocateDirect(part.indices.size*2).order(ByteOrder.nativeOrder()).also { it.asShortBuffer().put(part.indices) })
            meshes.add(Mesh(ids[0],ids[1],ids[2],part.indices.size))
        }
        val quads=IntArray(2); GLES20.glGenBuffers(2,quads,0); floorQuad=quads[0]; glowQuad=quads[1]
        upload(GLES20.GL_ARRAY_BUFFER,floorQuad,floats(floatArrayOf(-8f,0f,-8f,8f,0f,-8f,-8f,0f,8f,8f,0f,8f)))
        upload(GLES20.GL_ARRAY_BUFFER,glowQuad,floats(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)))
        GLES20.glClearColor(0f,0f,0f,0f)
    }
    fun resize(w: Int,h: Int) { width=w.coerceAtLeast(1); height=h.coerceAtLeast(1); GLES20.glViewport(0,0,width,height) }

    fun draw(state: SceneState) {
        val frame=scene.frame(state,width,height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        // Mirror image for the glossy studio floor, then the floor, then the car.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        drawCar(frame,state,true)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        drawFloor(frame,state)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        drawCar(frame,state,false)
        if(frame.glows.isNotEmpty()) drawGlows(frame)
    }
    private fun drawCar(frame: Frame,state: SceneState,mirror: Boolean) {
        GLES20.glUseProgram(car.id)
        GLES20.glUniformMatrix4fv(car.u("uViewProj"),1,false,frame.viewProj,0)
        GLES20.glUniform3f(car.u("uCam"),frame.eye.x,frame.eye.y,frame.eye.z)
        GLES20.glUniform1f(car.u("uLights"),state.lights)
        GLES20.glUniform1f(car.u("uMirror"),if(mirror) 1f else 0f)
        GLES20.glUniform1f(car.u("uStyle"),scene.style)
        GLES20.glUniform4fv(car.u("uBodyX"),1,scene.bodyX,0)
        GLES20.glUniform4fv(car.u("uBodyY"),1,scene.bodyY,0)
        GLES20.glUniform2fv(car.u("uDoor"),1,scene.door,0)
        GLES20.glUniform1f(car.u("uAlpha"),1f)
        GLES20.glEnableVertexAttribArray(0); GLES20.glEnableVertexAttribArray(1)
        for(call in frame.calls) {
            if(mirror && call.transparent) continue
            val mesh=meshes[call.mesh]
            GLES20.glUniformMatrix4fv(car.u("uModel"),1,false,call.model,0)
            GLES20.glUniformMatrix3fv(car.u("uNormalMat"),1,false,Mat4.normal(call.model),0)
            GLES20.glUniform1f(car.u("uMat"),call.material.id.toFloat())
            GLES20.glUniform3fv(car.u("uBase"),1,call.base,0)
            GLES20.glDepthMask(!call.transparent)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,mesh.positions); GLES20.glVertexAttribPointer(0,3,GLES20.GL_FLOAT,false,0,0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,mesh.normals); GLES20.glVertexAttribPointer(1,3,GLES20.GL_FLOAT,false,0,0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,mesh.indices)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,mesh.count,GLES20.GL_UNSIGNED_SHORT,0)
        }
        GLES20.glDepthMask(true)
        GLES20.glDisableVertexAttribArray(1)
    }
    private fun drawFloor(frame: Frame,state: SceneState) {
        GLES20.glUseProgram(floor.id)
        GLES20.glUniformMatrix4fv(floor.u("uViewProj"),1,false,frame.viewProj,0)
        GLES20.glUniform4fv(floor.u("uCar"),1,scene.floorCar,0)
        GLES20.glUniform2fv(floor.u("uTrack"),1,scene.floorTrack,0)
        GLES20.glUniform1f(floor.u("uLights"),state.lights)
        GLES20.glUniform1f(floor.u("uTravel"),state.travelMeters%1000f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,floorQuad)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0,3,GLES20.GL_FLOAT,false,0,0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
    }
    private fun drawGlows(frame: Frame) {
        GLES20.glUseProgram(glow.id)
        GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE)
        GLES20.glDepthMask(false)
        GLES20.glUniformMatrix4fv(glow.u("uViewProj"),1,false,frame.viewProj,0)
        GLES20.glUniform3f(glow.u("uRight"),frame.right.x,frame.right.y,frame.right.z)
        GLES20.glUniform3f(glow.u("uUp"),frame.up.x,frame.up.y,frame.up.z)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,glowQuad)
        GLES20.glEnableVertexAttribArray(0); GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,0,0)
        for(g in frame.glows) {
            GLES20.glUniform3f(glow.u("uCenter"),g.center.x,g.center.y,g.center.z)
            GLES20.glUniform3fv(glow.u("uColor"),1,g.color,0)
            GLES20.glUniform1f(glow.u("uSize"),g.size)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        }
        GLES20.glDepthMask(true)
        GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }
    fun release() {
        val ids=meshes.flatMap { listOf(it.positions,it.normals,it.indices) }.toIntArray()+intArrayOf(floorQuad,glowQuad)
        GLES20.glDeleteBuffers(ids.size,ids,0); meshes.clear()
        for(p in listOf(car,floor,glow)) GLES20.glDeleteProgram(p.id)
    }

    private fun floats(values: FloatArray)=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).also { it.asFloatBuffer().put(values) }
    private fun upload(target: Int,id: Int,data: ByteBuffer) {
        data.position(0)
        GLES20.glBindBuffer(target,id); GLES20.glBufferData(target,data.capacity(),data,GLES20.GL_STATIC_DRAW)
    }
    private fun shader(type: Int,source: String): Int {
        val id=GLES20.glCreateShader(type)
        GLES20.glShaderSource(id,source); GLES20.glCompileShader(id)
        val ok=IntArray(1); GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0)
        if(ok[0]==0) { val log=GLES20.glGetShaderInfoLog(id); GLES20.glDeleteShader(id); throw IllegalStateException("Shader: $log") }
        return id
    }
    private fun program(vertex: String,fragment: String): Int {
        val id=GLES20.glCreateProgram()
        val vs=shader(GLES20.GL_VERTEX_SHADER,vertex); val fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment)
        GLES20.glAttachShader(id,vs); GLES20.glAttachShader(id,fs)
        GLES20.glBindAttribLocation(id,0,"aPos"); GLES20.glBindAttribLocation(id,1,"aNormal"); GLES20.glBindAttribLocation(id,0,"aCorner")
        GLES20.glLinkProgram(id)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        val ok=IntArray(1); GLES20.glGetProgramiv(id,GLES20.GL_LINK_STATUS,ok,0)
        if(ok[0]==0) { val log=GLES20.glGetProgramInfoLog(id); GLES20.glDeleteProgram(id); throw IllegalStateException("Program: $log") }
        return id
    }
}
