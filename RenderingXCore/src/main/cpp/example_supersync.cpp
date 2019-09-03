//
// Created by Consti10 on 28/08/2019.
//
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <jni.h>
#include <glm/glm.hpp>
#include <glm/mat4x4.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/quaternion.hpp>
#include <glm/ext.hpp>
#include <glm/gtx/matrix_decompose.hpp>
#include <GLProgramVC.h>
#include <GLProgramText.h>
#include <Helper/ColoredGeometry.hpp>
#include <TextAssetsHelper.hpp>
#include <Helper/BasicGLPrograms.hpp>
#include <FBRManager.h>
#include <Extensions.hpp>

#include "GLProgramLine.h"
#include "Time/Chronometer.h"
#include "Time/FPSCalculator.h"
#include "Time/VRFrameCPUChronometer.h"
#include "example_supersync.h"

//Super sync (synchronous front buffer rendering) is a technique for reducing latency in VR
//It renders both the left and right eye alternating in sync with the VSYNC.
//Since it only halves the frame buffer, it can be both called scan line racing and scan line chasing

//This is just an example for showing the basics, see FPV-VR for a useful implementation
//It only clears the screen and renders a triangle from the left and right eye view


GLRSuperSyncExample::GLRSuperSyncExample(JNIEnv *env, jobject androidContext,
                                       bool qcomTiledRenderingAvailable,
                                       bool reusableSyncAvailable):
        mFrameTimeAcc(std::vector<std::string>{"startDR","drawEye","stopDR"}){
    std::function<void(JNIEnv *env2, bool whichEye, int64_t offsetNS)> f = [this](JNIEnv *env2, bool whichEye, int64_t offsetNS) {
        this->renderNewEyeCallback(env2,whichEye,offsetNS);
    };
    mFBRManager=std::make_unique<FBRManager>(qcomTiledRenderingAvailable,reusableSyncAvailable,f, nullptr);
}

void GLRSuperSyncExample::onSurfaceCreated(JNIEnv *env,jobject androidContext) {
    mBasicGLPrograms=std::make_unique<BasicGLPrograms>(false,nullptr);
    mBasicGLPrograms->text.loadTextRenderingData(env, androidContext,TextAssetsHelper::ARIAL_BOLD);
    mFrameTimeAcc.reset();
}

void GLRSuperSyncExample::onSurfaceChanged(int width, int height) {
    ViewPortW=width/2;
    ViewPortH=height;
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
    glClearColor(0.0F,0,0,0.0F);
    glEnable(GL_SCISSOR_TEST);
}

void GLRSuperSyncExample::enterSuperSyncLoop(JNIEnv *env, jobject obj,int exclusiveVRCore) {
    setAffinity(exclusiveVRCore);
    LOGD("entering superSync loop. GLThread will be blocked");
    mFBRManager->enterDirectRenderingLoop(env);
    LOGD("exited superSync loop. GLThread unblocked");
}

void GLRSuperSyncExample::exitSuperSyncLoop() {
    mFBRManager->requestExitSuperSyncLoop();
}

void GLRSuperSyncExample::setLastVSYNC(int64_t lastVSYNC) {
    mFBRManager->setLastVSYNC(lastVSYNC);
}


void GLRSuperSyncExample::renderNewEyeCallback(JNIEnv *env, bool whichEye, int64_t offsetNS) {
    swapColor++;
    if(swapColor>2){
        glClearColor(0.0f,0.0f,0.0f,0.0f);
        swapColor=0;
    }else{
        glClearColor(1.0f,1.0f,0.0f,0.0f);
    }
    VREyeDurations vrEyeTimeStamps{whichEye};
    mFBRManager->startDirectRendering(whichEye,ViewPortW,ViewPortH);
    if(mFBRManager->directRenderingMode==FBRManager::QCOM_TILED_RENDERING){
        //so we have to call glClear() before any OpenGL calls that affect framebuffer contents (e.g. draw())
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }
    vrEyeTimeStamps.setTimestamp("startDR");
    drawEye(env,whichEye);
    vrEyeTimeStamps.setTimestamp("drawEye");
    mFBRManager->stopDirectRendering(whichEye);
    vrEyeTimeStamps.setTimestamp("stopDR");
    //
    //vrEyeTimeStamps.print();
    mFrameTimeAcc.add(vrEyeTimeStamps);
    mFrameTimeAcc.printEveryXSeconds(5);

}

void GLRSuperSyncExample::drawEye(JNIEnv *env, bool whichEye) {

}


#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
      Java_constantin_renderingx_example_GLRSuperSyncExample_##method_name

inline jlong jptr(GLRSuperSyncExample *glRendererStereo) {
    return reinterpret_cast<intptr_t>(glRendererStereo);
}
inline GLRSuperSyncExample *native(jlong ptr) {
    return reinterpret_cast<GLRSuperSyncExample *>(ptr);
}

extern "C" {

JNI_METHOD(jlong, nativeConstruct)
(JNIEnv *env, jobject obj,jobject androidContext,jboolean qcomTiledRenderingAvailable,jboolean reusableSyncAvailable) {
    return jptr(new GLRSuperSyncExample(env,androidContext,(bool)qcomTiledRenderingAvailable,(bool)reusableSyncAvailable));
}
JNI_METHOD(void, nativeDelete)
(JNIEnv *env, jobject obj, jlong glRendererStereo) {
    delete native(glRendererStereo);
}

JNI_METHOD(void, nativeOnSurfaceCreated)
(JNIEnv *env, jobject obj, jlong glRendererStereo,jobject androidContext) {
    native(glRendererStereo)->onSurfaceCreated(env,androidContext);
}
JNI_METHOD(void, nativeOnSurfaceChanged)
(JNIEnv *env, jobject obj, jlong glRendererStereo,jint w,jint h) {
    native(glRendererStereo)->onSurfaceChanged(w, h);
}
JNI_METHOD(void, nativeEnterSuperSyncLoop)
(JNIEnv *env, jobject obj, jlong glRendererStereo,jint exclusiveVRCore) {
    LOGD("nativeEnterSuperSyncLoop()");
    native(glRendererStereo)->enterSuperSyncLoop(env,obj,(int)exclusiveVRCore);
}
JNI_METHOD(void, nativeExitSuperSyncLoop)
(JNIEnv *env, jobject obj, jlong glRendererStereo) {
    LOGD("nativeExitSuperSyncLoop()");
    native(glRendererStereo)->exitSuperSyncLoop();
}
JNI_METHOD(void, nativeDoFrame)
(JNIEnv *env, jobject obj, jlong glRendererStereo,jlong lastVSYNC) {
    native(glRendererStereo)->setLastVSYNC((int64_t) lastVSYNC);
}

}