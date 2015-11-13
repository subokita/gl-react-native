package com.projectseptember.RNGL;

import static android.opengl.GLES20.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class GLShader {

    private static final Logger logger = Logger.getLogger(GLShader.class.getName());

    private final String name;
    private final String vert;
    private final String frag;
    private Map<String, Integer> uniformTypes;
    private int program; // Program of the shader
    private int buffer[]; // the buffer currently contains 2 static triangles covering the surface
    private int pointerLoc; // The "pointer" attribute is used to iterate over vertex
    private Map<String, Integer> uniformLocations; // The uniform locations cache

    public GLShader(String name, String vert, String frag) {
        this.name = name;
        this.vert = vert;
        this.frag = frag;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (buffer != null) {
            glDeleteProgram(program);
            glDeleteBuffers(1, buffer, 0);
        }
    }

    public void bind () {
        if (!glIsProgram(program)) {
            logger.severe("Shader '"+name+"': not a program!");
            return;
        }
        glUseProgram(program);
        glBindBuffer(GL_ARRAY_BUFFER, buffer[0]);
        glEnableVertexAttribArray(pointerLoc);
        glVertexAttribPointer(pointerLoc, 2, GL_FLOAT, false, 0, 0);
    }

    public void validate () {
        glValidateProgram(program);
        int[] validSuccess = new int[1];
        glGetProgramiv(program, GL_VALIDATE_STATUS, validSuccess, 0);
        if (validSuccess[0] == GL_FALSE) {
            glGetProgramInfoLog(program);
            logger.severe("Shader '" + name + "': Validation failed " + glGetProgramInfoLog(program));
        }
    }

    public void setUniform (String name, Integer i) {
        glUniform1i(uniformLocations.get(name), i);
    }
    public void setUniform (String name, Float f) {
        glUniform1f(uniformLocations.get(name), f);
    }
    public void setUniform (String name, FloatBuffer buf, int type) {
        switch (type) {
            case GL_FLOAT_VEC2:
                glUniform2fv(uniformLocations.get(name), 1, buf);
                break;
            case GL_FLOAT_VEC3:
                glUniform3fv(uniformLocations.get(name), 1, buf);
                break;
            case GL_FLOAT_VEC4:
                glUniform4fv(uniformLocations.get(name), 1, buf);
                break;
            case GL_FLOAT_MAT2:
                glUniformMatrix2fv(uniformLocations.get(name), 1, false, buf);
                break;
            case GL_FLOAT_MAT3:
                glUniformMatrix3fv(uniformLocations.get(name), 1, false, buf);
                break;
            case GL_FLOAT_MAT4:
                glUniformMatrix4fv(uniformLocations.get(name), 1, false, buf);
                break;
            default:
                throw new Error("Unsupported case: uniform '" + name + "' type: " + type);
        }
    }
    public void setUniform (String name, IntBuffer buf, int type) {
        switch (type) {
            case GL_INT_VEC2:
            case GL_BOOL_VEC2:
                glUniform2iv(uniformLocations.get(name), 1, buf);
                break;
            case GL_INT_VEC3:
            case GL_BOOL_VEC3:
                glUniform3iv(uniformLocations.get(name), 1, buf);
                break;
            case GL_INT_VEC4:
            case GL_BOOL_VEC4:
                glUniform4iv(uniformLocations.get(name), 1, buf);
                break;
            default:
                throw new Error("Unsupported case: uniform '"+name+"' type: "+type);
        }
    }

    public String getName() {
        return name;
    }

    public Map<String, Integer> getUniformTypes() {
        return uniformTypes;
    }


    private static int compileShader (String name, String code, int shaderType) {
        int shaderHandle = glCreateShader(shaderType);
        glShaderSource(shaderHandle, code);
        glCompileShader(shaderHandle);
        int compileSuccess[] = new int[1];
        glGetShaderiv(shaderHandle, GL_COMPILE_STATUS, compileSuccess, 0);
        if (compileSuccess[0] == GL_FALSE) {
            logger.severe("Shader '"+name+"' failed to compile: "+glGetShaderInfoLog(shaderHandle));
            return -1;
        }
        return shaderHandle;
    }

    private void computeMeta () {
        Map<String, Integer> uniforms = new HashMap<>();
        Map<String, Integer> locations = new HashMap<>();

        int[] nbUniforms = new int[1];
        int[] type = new int[1];
        int[] size = new int[1];
        glGetProgramiv(program, GL_ACTIVE_UNIFORMS, nbUniforms, 0);
        for (int i=0; i < nbUniforms[0]; i++) {
            String uniformName = glGetActiveUniform(program, i, size, 0, type, 0);
            int location = glGetUniformLocation(program, uniformName);
            uniforms.put(uniformName, type[0]);
            locations.put(uniformName, location);
        }
        this.uniformTypes = uniforms;
        this.uniformLocations = locations;
    }

    private void makeProgram () {
        int vertex = compileShader(name, vert, GL_VERTEX_SHADER);
        if (vertex == -1) return;

        int fragment = compileShader(name, frag, GL_FRAGMENT_SHADER);
        if (fragment == -1) return;

        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);

        int[] linkSuccess = new int[1];
        glGetProgramiv(program, GL_LINK_STATUS, linkSuccess, 0);
        if (linkSuccess[0] == GL_FALSE) {
            logger.severe("Shader '"+name+"': Linking failed "+glGetProgramInfoLog(program));
            return;
        }

        glUseProgram(program);

        computeMeta();

        pointerLoc = glGetAttribLocation(program, "position");

        buffer = new int[1];
        glGenBuffers(1, buffer, 0);
        glBindBuffer(GL_ARRAY_BUFFER, buffer[0]);

        float buf[] = {
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f,  1.0f,
                -1.0f,  1.0f,
                1.0f, -1.0f,
                1.0f,  1.0f
        };
        FloatBuffer bufferData = ByteBuffer.allocateDirect(buf.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        bufferData.put(buf).position(0);

        glBufferData(GL_ARRAY_BUFFER, buf.length * 4, bufferData, GL_STATIC_DRAW);
    }

    public boolean isReady () {
        return buffer != null && uniformLocations != null;
    }

    public boolean ensureCompile() {
        if (!isReady()) makeProgram();
        return isReady();
    }
}
