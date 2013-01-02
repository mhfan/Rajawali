package rajawali.filters;

import rajawali.materials.AMaterial;
import android.opengl.GLES20;

public class TouchRippleFilter extends AMaterial implements IPostProcessingFilter {
	protected static final String mVShader =
			"uniform mat4 uMVPMatrix;\n" +
	
			"attribute vec4 aPosition;\n" +
			"attribute vec2 aTextureCoord;\n" +
			"attribute vec4 aColor;\n" +
			
			"%RIPPLE_PARAMS%\n" +
			
			"uniform float uTime;\n" +
			"uniform float uDuration;\n" +
			"uniform vec2 uRatio;\n" +
			"uniform float uRippleSpeed;\n" +
			"uniform float uRippleSize;\n" +
			
			"varying vec2 vTextureCoord;\n" +
			
			"vec2 processRipple(vec2 origin, float time) {\n" +
			"	if (time > uDuration) {\n"+
			"		return vec2(0.0, 0.0);\n" +
			"	}\n" +
			"	float complete = time / uDuration;\n" +
			"	vec2 pos = vec2(aPosition.y + .5, 1.0 - (aPosition.x + .5));" +
			"	pos *= uRatio;\n" +
			"	origin *= uRatio;\n" +
			"	float dist = distance(origin, pos);\n" +
			"	float rad = uRippleSpeed * time;\n" +
			"	float ripplePos = (dist - rad) / (uRippleSize / 2.0);\n" +
			"	if (-1.0 > ripplePos || ripplePos > 1.0) {\n" +
			"		return vec2(0.0, 0.0);\n" +
			"	}\n" +
			"	vec2 dir = normalize(origin - pos);\n" +
			"	float amplitude = pow(1.0 - complete, 3.0) * uRippleSize;\n" +
			"	vec2 displ = dir * sin(ripplePos * 3.14159) * amplitude;\n" +
			"	return displ;\n" +
			"}\n" +
			
			"void main() {\n" +
			"	gl_Position = uMVPMatrix * aPosition;\n" +
			"%RIPPLE_DISPLACE%" +
			"	vTextureCoord = aTextureCoord;\n" +
			"}\n";
		
	protected static final String mFShader = 
			"precision highp float;" +
			"varying vec2 vTextureCoord;" +

			"uniform sampler2D uFrameBufferTexture;" +
			
			"%RIPPLE_PARAMS%\n" +
			
			"void main() {\n" +
			"	vec2 texCoord = vTextureCoord;\n" +
			
			"%RIPPLE_DISPLACE%" +
			
			"	gl_FragColor = texture2D(uFrameBufferTexture, texCoord);\n" +
			"}";
	
	private int[] muRippleOriginHandles;
	
	private int[] muRippleStartHandles;
	
	private int muTimeHandle;
	private int muDurationHandle;
	private int muRatioHandle;
	private int muRippleSpeedHandle;
	private int muRippleSizeHandle;
	
	private float[][] mRipples;	
	private float[] mRippleStartTimes;
	
	private float mTime;
	private float mDuration = 3.0f;
	private float[] mRatio;
	private float mRippleSpeed = 0.3f;
	private float mRippleSize = 0.08f;
	private int mNumRipples;
	
	private int currentRippleIndex;
			
	public TouchRippleFilter() {
		this(3);
	}
	
	public TouchRippleFilter(int numRipples) {
		super(mVShader, mFShader, false);
		mNumRipples = numRipples;
		
		mRipples = new float[mNumRipples][2];
		mRippleStartTimes = new float[mNumRipples];
		muRippleOriginHandles = new int[mNumRipples];
		muRippleStartHandles = new int[mNumRipples];
		
		for(int i=0; i<mNumRipples; ++i) {
			mRippleStartTimes[i] = -1000;
		}
		mRatio = new float[] { 1, 1 };
		setShaders(mUntouchedVertexShader, mUntouchedFragmentShader);
	}
	
	public TouchRippleFilter(float rippleDuration, float rippleSpeed, float rippleSize) {
		this();
		mDuration = rippleDuration;
		mRippleSpeed = rippleSpeed;
		mRippleSize = rippleSize;
	}
	
	public boolean usesDepthBuffer() {
		return false;
	}
	
	@Override
	public void useProgram() {
		super.useProgram();
		for(int i=0; i<mNumRipples; ++i) {
			GLES20.glUniform2fv(muRippleOriginHandles[i], 1, mRipples[i], 0);
			GLES20.glUniform1f(muRippleStartHandles[i], mRippleStartTimes[i]);
		}
		GLES20.glUniform1f(muTimeHandle, mTime);
		GLES20.glUniform1f(muDurationHandle, mDuration);
		GLES20.glUniform2fv(muRatioHandle, 1, mRatio, 0);
		GLES20.glUniform1f(muRippleSizeHandle, mRippleSize);
		GLES20.glUniform1f(muRippleSpeedHandle, mRippleSpeed);
	}
	
	@Override
	public void setShaders(String vertexShader, String fragmentShader)
	{
		StringBuffer params = new StringBuffer();
		StringBuffer vertDispl = new StringBuffer();
		StringBuffer fragDispl = new StringBuffer();
		
		for(int i=0; i<mNumRipples; ++i) {
			params.append("uniform vec2 uRipple").append(i).append("Origin;\n");
			params.append("uniform float uRipple").append(i).append("Start;\n");
			params.append("varying vec2 vDisplace").append(i).append(";\n");
			
			vertDispl.append("vDisplace").append(i).append(" = processRipple(uRipple").append(i).append("Origin , uTime - uRipple").append(i).append("Start);\n");
			
			fragDispl.append("texCoord += vDisplace").append(i).append(";\n");
		}
		
		super.setShaders(
				vertexShader.replace("%RIPPLE_PARAMS%", params.toString()).replace("%RIPPLE_DISPLACE%", vertDispl.toString()), 
				fragmentShader.replace("%RIPPLE_PARAMS%", params.toString()).replace("%RIPPLE_DISPLACE%", fragDispl.toString())
				);
		
		for(int i=0; i<mNumRipples; ++i) {
			muRippleOriginHandles[i] = getUniformLocation("uRipple"+i+"Origin");
			muRippleStartHandles[i] = getUniformLocation("uRipple"+i+"Start");
		}
		
		muTimeHandle = getUniformLocation("uTime");
		muDurationHandle = getUniformLocation("uDuration");
		muRatioHandle = getUniformLocation("uRatio");
		muRippleSizeHandle = getUniformLocation("uRippleSize");
		muRippleSpeedHandle = getUniformLocation("uRippleSpeed");
	}

	public void addRipple(float x, float y, float startTime) {
		mRipples[currentRippleIndex][0] = x;
		mRipples[currentRippleIndex][1] = y;
		mRippleStartTimes[currentRippleIndex] = startTime;
		currentRippleIndex++;
		if (currentRippleIndex == mNumRipples)
			currentRippleIndex = 0;
	}

	/**
	 * @deprecated Replaced by {@link #addRipple}
	 */
	@Deprecated
	public void addTouch(float x, float y, float startTime) {
		addRipple(x, y, startTime);
	}

	public void setTime(float time) {
		mTime = time;
	}

	public float getDuration() {
		return mDuration;
	}

	public void setDuration(float duration) {
		this.mDuration = duration;
	}
	
	public void setScreenSize(float width, float height) {
		if(width > height) {
			mRatio[0] = 1;
			mRatio[1] = height / width;
		} else if(height > width) {
			mRatio[0] = width / height;
			mRatio[1] =  1;
		} else {
			mRatio[0] = 1;
			mRatio[1] = 1;
		}
	}

	public float getRippleSpeed() {
		return mRippleSpeed;
	}

	public void setRippleSpeed(float rippleSpeed) {
		this.mRippleSpeed = rippleSpeed;
	}

	public float getRippleSize() {
		return mRippleSize;
	}

	public void setRippleSize(float rippleSize) {
		this.mRippleSize = rippleSize;
	}
}
