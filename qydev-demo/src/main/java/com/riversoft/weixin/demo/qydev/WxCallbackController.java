package com.riversoft.weixin.demo.qydev;

import ch.qos.logback.core.rolling.helper.FileStoreUtil;
import com.riversoft.weixin.common.WxClient;
import com.riversoft.weixin.common.decrypt.MessageDecryption;
import com.riversoft.weixin.common.exception.WxRuntimeException;
import com.riversoft.weixin.common.jsapi.JsAPISignature;
import com.riversoft.weixin.common.message.XmlMessageHeader;
import com.riversoft.weixin.demo.commons.DuplicatedMessageChecker;
import com.riversoft.weixin.demo.qydev.utils.Constant;
import com.riversoft.weixin.demo.qydev.utils.FileUploadUtils;
import com.riversoft.weixin.qy.QyWxClientFactory;
import com.riversoft.weixin.qy.base.CorpSetting;
import com.riversoft.weixin.qy.contact.Users;
import com.riversoft.weixin.qy.contact.user.ReadUser;
import com.riversoft.weixin.qy.jsapi.JsAPIs;
import com.riversoft.weixin.qy.media.Medias;
import com.riversoft.weixin.qy.message.QyXmlMessages;
import com.riversoft.weixin.qy.oauth2.QyOAuth2s;
import com.riversoft.weixin.qy.oauth2.bean.QyUser;
import com.sun.imageio.plugins.common.ImageUtil;
import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.util.JSONPObject;
import org.codehaus.jackson.type.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by exizhai on 10/7/2015.
 */
@RestController
public class WxCallbackController {

    private static Logger logger = LoggerFactory.getLogger(WxCallbackController.class);

    /**
     * token/aesKey 是和企业号应用绑定的，这里为了演示方便使用外部注入，实际使用的时候一个企业号可能有多个应用，这样的话需要有个分发逻辑。
     * 比如callback url可以定义为  /wx/qy/[应用的ID]，通过ID查询不同的token和aesKey
     */
    @Value("${agent.default.token}")
    private String token;

    @Value("${agent.default.aesKey}")
    private String aesKey;

    @Autowired
    private DuplicatedMessageChecker duplicatedMessageChecker;

    public void setDuplicatedMessageChecker(DuplicatedMessageChecker duplicatedMessageChecker) {
        this.duplicatedMessageChecker = duplicatedMessageChecker;
    }

    /**
     * 企业号回调接口
     * 这里为了演示方便使用单个URL，实际使用的时候一个企业号可能有多个应用，这样的话需要有个分发逻辑：
     * 比如callback url可以定义为  /wx/qy/[应用的ID]，通过ID查询不同的token和aesKey
     *
     * @param signature
     * @param timestamp
     * @param nonce
     * @param echostr
     * @param content
     * @return
     */
    @RequestMapping("/wx/qy")
    @ResponseBody
    public String qy(@RequestParam(value="msg_signature") String signature,
                           @RequestParam(value="timestamp") String timestamp,
                           @RequestParam(value="nonce") String nonce,
                           @RequestParam(value="echostr", required = false) String echostr,
                           @RequestBody(required = false) String content) {

        logger.info("msg_signature={}, nonce={}, timestamp={}, echostr={}", signature, nonce, timestamp, echostr);

        CorpSetting corpSetting = CorpSetting.defaultSettings();

        try {
            MessageDecryption messageDecryption = new MessageDecryption(token, aesKey, corpSetting.getCorpId());
            if (!StringUtils.isEmpty(echostr)) {
                String echo = messageDecryption.decryptEcho(signature, timestamp, nonce, echostr);
                logger.info("消息签名验证成功.");
                return echo;
            } else {
                XmlMessageHeader xmlRequest = QyXmlMessages.fromXml(messageDecryption.decrypt(signature, timestamp, nonce, content));
                XmlMessageHeader xmlResponse = qyDispatch(xmlRequest);
                if(xmlResponse != null) {
                    try {
                        return messageDecryption.encrypt(QyXmlMessages.toXml(xmlResponse), timestamp, nonce);
                    } catch (WxRuntimeException e) {
                    }
                }
            }
        } catch (Exception e) {
            logger.error("callback failed.", e);
        }

        return "";
    }

    @RequestMapping(value = "/wx/sigin")
    public String Test(@RequestParam(value="url") String url,HttpServletRequest request){
        String mapJakcson="";
        Object object = request.getSession().getAttribute("mapJakcson");
        if (object!=null){
            return object.toString();
        }else {
            JsAPISignature jsAPISignature =  JsAPIs.defaultJsAPIs().createJsAPISignature(url);
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapJakcson=mapper.writeValueAsString(jsAPISignature);
                request.getSession().setAttribute("mapJakcson",mapJakcson);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return mapJakcson;
    }

    @RequestMapping(value = "/wx/upload")
    public String upload(@RequestParam(value="param") String param,HttpServletRequest request){
        String mapJakcson="";
        Map<String, File> fileMap = new HashMap<String, File>();
        Map<String, String> textMap = new HashMap<String, String>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JavaType javaType =  mapper.getTypeFactory().constructParametricType(ArrayList.class, Media.class);
            List<Media> mediaList = mapper.readValue(param,javaType);
            for (Media m: mediaList
                 ) {
                File file = Medias.defaultMedias().download(m.getServerid());
//                saveImg(file);
                fileMap.put(file.getName(), file);

            }
            String contentType = ".jpg";//image/png
            mapJakcson= FileUploadUtils.formFileUpload(Constant.ORDER_UPLOAD_PIC, textMap, fileMap,contentType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mapJakcson;
    }

    //TODO 有问题的方法
    @RequestMapping(value = "/wx/code")
    public String auth(@RequestParam(value="code") String code,HttpServletRequest request){
        String mapJakcson="";
        QyUser qyUser = QyOAuth2s.defaultOAuth2s().userInfo(code);
        String result = QyOAuth2s.defaultOAuth2s().toUserId(qyUser.getOpenId());
        System.out.println(code);
        System.out.println(result);
        String userid =QyOAuth2s.defaultOAuth2s().toUserId(qyUser.getOpenId());
        ReadUser readUser = Users.defaultUsers().get(result);
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapJakcson=mapper.writeValueAsString(readUser);
            System.out.println(mapJakcson);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return mapJakcson;
    }

    private String saveImg(File file){
        if (!file.exists()) {
            return "文件为空";
        }
        // 获取文件名
        String fileName = file.getName();
        logger.info("上传的文件名为：" + fileName);
        // 获取文件的后缀名
        String suffixName = fileName.substring(fileName.lastIndexOf("."));
        logger.info("上传的后缀名为：" + suffixName);
        // 文件上传后的路径
        String filePath = "E://test//";
        // 解决中文问题，liunx下中文路径，图片显示问题
        // fileName = UUID.randomUUID() + suffixName;
        File dest = new File(filePath + fileName);
        // 检测是否存在目录
        if (!dest.getParentFile().exists()) {
            dest.getParentFile().mkdirs();
        }
        try {
            FileUtils.copyFile(file,dest);
            return "上传成功";
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
    private XmlMessageHeader qyDispatch(XmlMessageHeader xmlRequest) {
        //添加处理逻辑

        //需要同步返回消息（被动响应消息）给用户则构造一个XmlMessageHeader类型，比较鸡肋，因为处理逻辑如果比较复杂响应太慢会影响用户感知，建议直接返回null；
        //如果有消息需要发送给用户则可以调用主动消息发送接口进行异步发送
        return null;
    }


}
class Media{
    String id;
    String serverid;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServerid() {
        return serverid;
    }

    public void setServerid(String serverid) {
        this.serverid = serverid;
    }
}