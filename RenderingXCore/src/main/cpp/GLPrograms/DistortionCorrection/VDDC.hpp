//
// Created by Constantin on 2/17/2019.
//

#ifndef FPV_VR_VDDC_H
#define FPV_VR_VDDC_H

#include <array>
#include <string>
#include <sstream>
#include <Helper/MDebug.hpp>
#include <vector>
#include "Helper/GLHelper.hpp"

#include "vr/gvr/capi/include/gvr.h"
#include "vr/gvr/capi/include/gvr_types.h"

#include <glm/glm.hpp>
#include <glm/mat4x4.hpp>
#include <glm/gtc/matrix_transform.hpp>

#include "android/log.h"

#include "DistortionCorrection/Distortion.h"

/// VDDC Vertex displacement distortion correction
/// The distortion happens in view space
/// This space ranges from -1 ... 1, and maxRadSq would be therefore 1 without any distortion.
/// But since the distortion moves coordinate points 'inwarts' relative to the view space r2,
/// we have to distort maxRadSq before


class DistortionManager{
public:
    static constexpr const int N_UNDISTORTION_COEFICIENTS=7;
    std::array<float,N_UNDISTORTION_COEFICIENTS> VR_DC_UndistortionData;

    static constexpr const int RESOLUTION_XY=20;
    static constexpr int ARRAY_SIZE=RESOLUTION_XY*RESOLUTION_XY;
    float lol[RESOLUTION_XY][RESOLUTION_XY][2];
public:
    DistortionManager(gvr_context* gvrContext){
        const Distortion mDistortion(gvrContext,400);
        const Distortion inverse=mDistortion.calculateInverse(RESOLUTION_XY);
        inverse.lol(lol);
        //0.4331, -0.0856, 1.4535
        VR_DC_UndistortionData.at(0)=10.0f;
        VR_DC_UndistortionData.at(1)=0.34f;
        VR_DC_UndistortionData.at(2)=0.55f;
        VR_DC_UndistortionData.at(2)=1.4535f;
    }
    void doLOL(const GLuint lolHandle)const{
        glUniform2fv(lolHandle,(GLsizei)(ARRAY_SIZE),(GLfloat*)lol);
    }
};


class VDDC {
public:

    static const std::string writeGLPosition(const DistortionManager* distortionManager,const std::string &positionAttribute="aPosition"){
        if(distortionManager!= nullptr)return writeGLPositionWithVDDC(positionAttribute);
        //return"vec4 lul=uMVMatrix * "+positionAttribute+";\n"+"";
        return "gl_Position = (uPMatrix*uMVMatrix)* "+positionAttribute+";\n";
        //return "gl_Position = vec4("+positionAttribute+".xy*2.0, 0, 1);";
    }
    static constexpr int RESOLUTION_XY=20;
    static const std::string writeGLPositionWithVDDC_1(const std::string& positionAttribute){
        std::stringstream s;
        s<<"vec4 pos=uMVMatrix*"+positionAttribute+";\n";
        s<<"float r2=dot(pos.xy,pos.xy)/(pos.z*pos.z);\n";
        s<<"r2=clamp(r2,0.0,_MaxRadSq);\n";
        s<<"float ret = 0.0;\n";
        s<<"ret = r2 * (ret + _Undistortion2.y);\n";
        s<<"ret = r2 * (ret + _Undistortion2.x);\n";
        s<<"ret = r2 * (ret + _Undistortion.w);\n";
        s<<"ret = r2 * (ret + _Undistortion.z);\n";
        s<<"ret = r2 * (ret + _Undistortion.y);\n";
        s<<"ret = r2 * (ret + _Undistortion.x);\n";
        //--
        //s<<"if(ret < -1.0){";
        //s<<" ret=-1.0;";
        //s<<"}";
        //s<<" if( sqrt(r2) > 1.0 ){";    //|| r2<__MaxRadSq
        //s<<" r2=_MaxRadSq;";
        //s<<" pos.x=0.0;";
        //s<<" pos.y=0.0;";
        //s<<" pos.z=pos.z*pos.z;";
        //s<<" ret=0.0;\n";
        //s<<" };";
        //--
        s<<"pos.xy*=1.0+ret;\n";
        s<<"gl_Position=pos;\n";
        return s.str();
    }
    static const std::string writeGLPositionWithVDDC_2(const std::string& positionAttribute){
        std::stringstream s;
        s<<std::fixed;
        //s<<"  pos=vec4("+positionAttribute+".xy*2.0, 0, 1);";
        s<<"vec4  pos=(uPMatrix*uMVMatrix)*"+positionAttribute+";\n";
        s<<"vec2 ndc=pos.xy/pos.w;";///(-pos.z);";
        s<<"int idx1=my_round(ndc.x*"<<(DistortionManager::RESOLUTION_XY/2.0f)<<")+"<<DistortionManager::RESOLUTION_XY/2<<";";
        s<<"int idx2=my_round(ndc.y*"<<(DistortionManager::RESOLUTION_XY/2.0f)<<")+"<<DistortionManager::RESOLUTION_XY/2<<";";
        s<<"idx1=my_clamp(idx1,0,"<<DistortionManager::RESOLUTION_XY-1<<");";
        s<<"idx2=my_clamp(idx2,0,"<<DistortionManager::RESOLUTION_XY-1<<");";
        //We do not have support for 2-dimensional arrays- simple pointer arithmetic ( array[i][j]==array[i*n+j] )
        s<<"int idx=idx1*"<<DistortionManager::RESOLUTION_XY<<"+idx2;";
        s<<"pos.x+=LOL[idx].x;";
        s<<"pos.y+=LOL[idx].y;";
        s<<"gl_Position=pos;\n";
        return s.str();
    }
    static const std::string writeGLPositionWithVDDC(const std::string& positionAttribute){
        //return writeGLPositionWithVDDC_1(positionAttribute);
        return writeGLPositionWithVDDC_2(positionAttribute);
    }

    static const std::string writeLOL(const DistortionManager* distortionManager){
        if(distortionManager== nullptr)return "uniform highp vec2 LOL[1];"; //dummy so we don't get compilation issues when querying handle
        const bool secondVersion=true;
        std::stringstream s;
        if(secondVersion){
            s<<"uniform highp vec2 LOL["<<DistortionManager::ARRAY_SIZE<<"];";
            s<<"int my_clamp(in int x,in int minVal,in int maxVal){";
            s<<"if(x<minVal){return minVal;}";
            s<<"if(x>maxVal){return maxVal;}";
            s<<"return x;}";
            s<<"int my_round(in float x){";
            s<<"float afterCome=x-float(int(x));";
            s<<"if(afterCome>=0.5){return int(x+0.5);}";
            s<<"return int(x);}";
        }else{
            const auto coeficients=distortionManager->VR_DC_UndistortionData;
            s<<std::fixed;
            s<<"uniform highp vec2 LOL[1];";
            s<<"const float _MaxRadSq="<<coeficients[0];s<<";\n";
            //There is no vec6 data type. Therefore, we use 1 vec4 and 1 vec2. Vec4 holds k1,k2,k3,k4 and vec6 holds k5,k6
            s<<"const vec4 _Undistortion=vec4("<<coeficients[1]<<","<<coeficients[2]<<","<<coeficients[3]<<","<<coeficients[4]<<");\n";
            s<<"const vec2 _Undistortion2=vec2("<<coeficients[5]<<","<<coeficients[6]<<");\n";
        }
        //LOGD("%s",s.str().c_str());
        return s.str();
    }

};




#endif //FPV_VR_VDDC_H
