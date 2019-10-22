//
// Created by Consti10 on 15/05/2019.
//

#include "example_renderer2.h"
#include "vr/gvr/capi/include/gvr.h"
#include "vr/gvr/capi/include/gvr_types.h"


ExampleRenderer2::ExampleRenderer2(JNIEnv *env, jobject androidContext,gvr_context *gvr_context,jfloatArray undistData) {
    /*jfloat *arrayP=env->GetFloatArrayElements(undistData, nullptr);
    std::memcpy(VR_DC_UndistortionData.data(),arrayP,VR_DC_UndistortionData.size()*sizeof(float));
    env->ReleaseFloatArrayElements(undistData,arrayP,0);*/
    //0.4331, -0.0856, 1.4535
    //VR_DC_UndistortionData.at(0)=10.0f;
    //VR_DC_UndistortionData.at(1)=0.34f;
    //VR_DC_UndistortionData.at(2)=0.55f;
    //VR_DC_UndistortionData.at(2)=1.4535f;
    gvr_api_=gvr::GvrApi::WrapNonOwned(gvr_context);
}

static std::vector<GLProgramVC::Vertex> distortVertices(const gvr_context *gvr_context,const std::vector<GLProgramVC::Vertex> input){
    const int RES=800;
    const auto mDistortion=Distortion(gvr_context,RES);
    const auto inverse=mDistortion.calculateInverse(8);

    std::vector<GLProgramVC::Vertex> ret(input.size());
    for(int i=0;i<input.size();i++){
        const GLProgramVC::Vertex& vOriginal=input.at(i);
        GLProgramVC::Vertex v{vOriginal};
        if(true){
            const auto distortion=inverse.distortPoint({v.x+0.5f,v.y+0.5f});
            v.x=(distortion.x-0.5f);
            v.y=(distortion.y-0.5f);
        }
        ret.at(i)=v;
    }
    return ret;
}


void ExampleRenderer2::onSurfaceCreated(JNIEnv *env, jobject context) {
//Instantiate all our OpenGL rendering 'Programs'
    glProgramVC=new GLProgramVC();

    distortionManager=new DistortionManager(gvr_api_->GetContext());

    glProgramVC2=new GLProgramVC(distortionManager);

    glProgramLine=new GLProgramLine();
    glProgramText=new GLProgramText();
    glProgramText->loadTextRenderingData(env,context,TextAssetsHelper::ARIAL_PLAIN);
    GLuint texture;
    glGenTextures(1,&texture);
    glProgramTexture=new GLProgramTextureExt(texture,false,distortionManager);

    glProgramTexture->loadTexture(env,context,"black_grid2_1.png");
    //create all the gl Buffer for later use
    glGenBuffers(1,&glBufferVC);
    glGenBuffers(1,&glBufferVCDistorted1);
    glGenBuffers(1,&glBufferVCDistorted2);
    glGenBuffers(1,&glBufferCoordinateSystemLines);
    //create the geometry for our simple test scene
    float size=1.0f;
    const auto tmp=ColoredGeometry::makeTesselatedColoredRectLines(TESSELATION,{-size/2.0f,-size/2.0f,0},size,size,Color::WHITE);
    GLHelper::allocateGLBufferStatic(glBufferVC,(void*)tmp.data(),tmp.size()*sizeof(GLProgramVC::Vertex));
    auto tmp1=distortVertices(gvr_api_->GetContext(),tmp);
    GLHelper::allocateGLBufferStatic(glBufferVCDistorted1,(void*)tmp1.data(),tmp1.size()*sizeof(GLProgramVC::Vertex));
    auto tmp2=distortVertices(gvr_api_->GetContext(),tmp);
    GLHelper::allocateGLBufferStatic(glBufferVCDistorted1,(void*)tmp2.data(),tmp2.size()*sizeof(GLProgramVC::Vertex));

    //make the line going trough (0,0)
    GLProgramVC::Vertex coordinateSystemLineVertices[4];
    ColoredGeometry::makeColoredLine(coordinateSystemLineVertices,0,glm::vec3(-100,0,0),glm::vec3(100,0,0),Color::YELLOW,Color::YELLOW);
    ColoredGeometry::makeColoredLine(&coordinateSystemLineVertices[2],0,glm::vec3(0,-100,0),glm::vec3(0,100,0),Color::YELLOW,Color::YELLOW);
    GLHelper::allocateGLBufferStatic(glBufferCoordinateSystemLines,coordinateSystemLineVertices,sizeof(coordinateSystemLineVertices));

    //Textured stuff
    //const float fov=90.0f;
    const float sizeX=1.0f;
    const float sizeY=1.0f;
    glGenBuffers(1,&glBufferTextured);
    glGenBuffers(1,&glBufferTextured1);
    glGenBuffers(1,&glBufferTextured2);
    glGenBuffers(1,&glBufferTexturedIndices);
    GLProgramTextureExt::Vertex texturedVertices[(TEXTURE_TESSELATION_FACTOR+1)*(TEXTURE_TESSELATION_FACTOR+1)];
    GLushort texturedIndices[6*TEXTURE_TESSELATION_FACTOR*TEXTURE_TESSELATION_FACTOR];
    TexturedGeometry::makeTesselatedVideoCanvas( texturedVertices,  texturedIndices, glm::vec3(-sizeX/2.0f,-sizeY/2.0f,0),
                                                sizeX,sizeY, TEXTURE_TESSELATION_FACTOR, 0.0f,1.0f);
    const int RESOULTION_CALCULATE_UNDISTORTION=400;
    //const auto distortedPoints=VDDC::calculateDistortedPoints(gvr_api_->GetContext(),RESOULTION_CALCULATE_UNDISTORTION);

    GLProgramTextureExt::Vertex texturedVertices1[(TEXTURE_TESSELATION_FACTOR+1)*(TEXTURE_TESSELATION_FACTOR+1)];
    //GLProgramTextureExt::Vertex texturedVertices2[(TEXTURE_TESSELATION_FACTOR+1)*(TEXTURE_TESSELATION_FACTOR+1)];

    for(int i=0;i<(TEXTURE_TESSELATION_FACTOR+1)*(TEXTURE_TESSELATION_FACTOR+1);i++){
        const GLProgramTextureExt::Vertex& v=texturedVertices[i];
        gvr_vec2f out[3];
        gvr_compute_distorted_point(gvr_api_.get()->GetContext(),GVR_LEFT_EYE,{v.u,v.v},out);
        //v.u=out[0].x;
        //v.v=out[0].y;
        texturedVertices1[i]={v.x,v.y,v.z,out[0].x,out[0].y};

        //gvr_compute_distorted_point(gvr_api_.get()->GetContext(),GVR_RIGHT_EYE,{v.u,v.v},out);
        //v.u=out[0].x;
        //v.v=out[0].y;
        //texturedVertices2[i]={v.x,v.y,v.z,out[0].x,out[0].y};

        //
        /*gvr_compute_distorted_point(gvr_api_.get()->GetContext(),GVR_LEFT_EYE,{v.x+0.5f,v.y+0.5f},out);
        v.x=(out[0].x-0.5f); //v.x*2-
        v.y=(out[0].y-0.5f);*/
        /*const auto la=VDDC::distortPointInverse(distortedPoints,RESOULTION_CALCULATE_UNDISTORTION,{v.x+0.5f,v.y+0.5f});
        v.x=(la.x-0.5f);
        v.y=(la.y-0.5f);*/
        /*uv.x=v.x+0.5f;
        uv.y=v.y+0.5f;
        uv=VDDC::findBestInverseDistortion(distortedPoints,RESOULTION_CALCULATE_UNDISTORTION,uv);
        v.x=(uv.x-0.5f);
        v.y=(uv.y-0.5f);*/
        /*float r2=sqrt((v.x*v.x)+(v.y*v.y));
        float dist=VDDC::calculateBrownConrady(r2,0.34f,0.55f);
        v.x*=dist;
        v.y*=dist;*/
    }
    GLHelper::allocateGLBufferStatic(glBufferTextured,texturedVertices,sizeof(texturedVertices));
    GLHelper::allocateGLBufferStatic(glBufferTextured1,texturedVertices1,sizeof(texturedVertices));
    //GLHelper::allocateGLBufferStatic(glBufferTextured2,texturedVertices2,sizeof(texturedVertices));
    GLHelper::allocateGLBufferStatic(glBufferTexturedIndices,texturedIndices,sizeof(texturedIndices));

    GLHelper::checkGlError("example_renderer::onSurfaceCreated");
}

void ExampleRenderer2::onSurfaceChanged(int width, int height) {
    ViewPortW=width/2;
    ViewPortH=height;
    projection=glm::perspective(glm::radians(90.0F), ((float) ViewPortW) / ((float) ViewPortH), MIN_Z_DISTANCE, MAX_Z_DISTANCE);
    glm::vec3 cameraPos   = glm::vec3(0,0,CAMERA_POSITION);
    glm::vec3 cameraFront = glm::vec3(0.0F,0.0F,-1.0F);
    eyeView=glm::lookAt(cameraPos,cameraPos+cameraFront,glm::vec3(0,1,0));
    //eyeView=glm::mat4();
    leftEyeView=glm::translate(eyeView,glm::vec3(0,0,0)); //-VR_InterpupilaryDistance/2.0f
    rightEyeView=glm::translate(eyeView,glm::vec3(0,0,0)); //VR_InterpupilaryDistance/2.0f
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glViewport(0,0,width,height);
}

void ExampleRenderer2::onDrawFrame() {
    glClearColor(0,0,0.2,0);
    glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    cpuFrameTime.start();
    drawEye(false);
    drawEye(true);
    GLHelper::checkGlError("example_renderer::onDrawFrame");
    cpuFrameTime.stop();
    cpuFrameTime.printAvg(5000);
    fpsCalculator.tick();
}

void ExampleRenderer2::drawEye(bool leftEye) {
    if(leftEye){
        glViewport(0,0,ViewPortW,ViewPortH);
    }else{
        glViewport(ViewPortW,0,ViewPortW,ViewPortH);
    }
    glm::mat4 tmp=leftEye ? leftEyeView : rightEyeView;

    //GLuint buff=whichEye ? glBufferVCDistorted1 : glBufferVCDistorted2;
    /*GLuint buff=glBufferVC;
    glProgramVC->beforeDraw(buff);
    glProgramVC->draw(glm::value_ptr(tmp),glm::value_ptr(projection),0,N_COLORED_VERTICES,GL_TRIANGLES);
    glProgramVC->afterDraw();*/

    glm::mat4 transform=glm::mat4();
    //transform=glm::rotate(transform,glm::radians(20.0f),glm::vec3(1.0f,0.0f,0.0f));
    transform=glm::translate(transform,glm::vec3(0.3f,0.0f,0.0f));

    glProgramTexture->beforeDraw(glBufferTextured1);
    glProgramTexture->drawIndexed(eyeView,projection,0,N_TEXTURED_INDICES,glBufferTexturedIndices);
    glProgramTexture->afterDraw();

    /*glProgramVC->beforeDraw(glBufferVCDistorted1);
    glProgramVC->draw(glm::value_ptr(eyeView),glm::value_ptr(projection),0,N_COLORED_VERTICES,GL_LINES);
    glProgramVC->afterDraw();*/

    /*glProgramVC->beforeDraw(glBufferVC);
    glProgramVC->draw(glm::value_ptr(eyeView),glm::value_ptr(projection),0,N_COLORED_VERTICES,GL_LINES);
    glProgramVC->afterDraw();*/

    glProgramVC2->beforeDraw(glBufferVC);
    glProgramVC2->draw(glm::value_ptr(tmp),glm::value_ptr(projection),0,N_COLORED_VERTICES,GL_LINES);
    glProgramVC2->afterDraw();

    /*glProgramVC->beforeDraw(glBufferCoordinateSystemLines);
    glProgramVC->draw(glm::value_ptr(eyeView),glm::value_ptr(projection),0,4,GL_LINES);
    glProgramVC->afterDraw();*/

}


#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
      Java_constantin_renderingx_example_renderer2_GLRTest_##method_name


inline jlong jptr(ExampleRenderer2 *p) {
    return reinterpret_cast<intptr_t>(p);
}
inline ExampleRenderer2 *native(jlong ptr) {
    return reinterpret_cast<ExampleRenderer2*>(ptr);
}

extern "C" {

JNI_METHOD(jlong, nativeConstruct)
(JNIEnv *env, jobject obj,jobject androidContext,jfloatArray undistortionData,jlong native_gvr_api) {
    return jptr(new ExampleRenderer2(env,androidContext,reinterpret_cast<gvr_context *>(native_gvr_api),undistortionData));
}
JNI_METHOD(void, nativeDelete)
(JNIEnv *env, jobject obj, jlong p) {
    delete native(p);
}

JNI_METHOD(void, nativeOnSurfaceCreated)
(JNIEnv *env, jobject obj,jlong p,jobject androidContext) {
    native(p)->onSurfaceCreated(env,androidContext);
}

JNI_METHOD(void, nativeOnSurfaceChanged)
(JNIEnv *env, jobject obj,jlong p,jint width,jint height) {
    native(p)->onSurfaceChanged((int)width,(int)height);
}

JNI_METHOD(void, nativeOnDrawFrame)
(JNIEnv *env, jobject obj,jlong p) {
    native(p)->onDrawFrame();
}

}
