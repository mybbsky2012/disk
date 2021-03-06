package com.aiyi.disk.disk.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.sql.where.C;
import com.aiyi.disk.disk.controller.FileController;
import com.aiyi.disk.disk.dao.ShareInfoDao;
import com.aiyi.disk.disk.dao.UserDao;
import com.aiyi.disk.disk.entity.ShareInfoPO;
import com.aiyi.disk.disk.entity.UserPO;
import com.aiyi.disk.disk.service.ShareInfoService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.validation.ValidationException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author gsk
 * @description: TODO
 * @date 2019/09/29
 * @email 719348277@qq.com
 */
@Service
public class ShareInfoServiceImpl implements ShareInfoService {

    @Resource
    private ShareInfoDao shareInfoDao;

    @Resource
    private UserDao userDao;

    @Override
    public ShareInfoPO create(ShareInfoPO shareInfoPO) {
        if (shareInfoPO.getSpeed() != null){
            if(shareInfoPO.getSpeed().intValue() < 100 || shareInfoPO.getSpeed().intValue() > 102400){
                throw new ValidationException("下载限速只允许设置100Kb/s~102400Kb/s之间");
            }
        }
        shareInfoDao.add(shareInfoPO);
        return shareInfoPO;
    }

    @Override
    public ShareInfoPO getById(String id) {
        ShareInfoPO shareInfoPO = shareInfoDao.get(id);
        if (null == shareInfoPO){
            return null;
        }
        UserPO userPO = userDao.get(shareInfoPO.getUid());

        shareInfoPO.setUsername(userPO.getUsername());
        shareInfoPO.setNickerName(userPO.getNickerName());
        shareInfoPO.setAvatar(userPO.getAvatar());

        OSS client = new OSSClientBuilder().build(shareInfoPO.getEndPoint(), shareInfoPO.getAccessKey(),
                shareInfoPO.getAccessKeySecret());

        if (shareInfoPO.getFileKey().contains("+")){
            String substring = shareInfoPO.getFileKey().substring(0, shareInfoPO.getFileKey().indexOf("+"));
            // 指定每页200个文件。
            final int maxKeys = 200;
            String nextMarker = null;
            ObjectListing objectListing;

            do {
                objectListing = client.listObjects(new ListObjectsRequest(shareInfoPO.getBucketName()).
                        withPrefix(substring).withMarker(nextMarker).withMaxKeys(maxKeys));

                List<OSSObjectSummary> sums = objectListing.getObjectSummaries();
                for (OSSObjectSummary summary : sums) {
                    if (summary.getKey().equals(shareInfoPO.getFileKey())){
                        shareInfoPO.setSize(FileController.getFileSize2Str(summary.getSize()));
                        if (summary.getLastModified().getTime() > shareInfoPO.getCreateTime().getTime()){
                            shareInfoPO.setCreateTime(summary.getLastModified());
                        }
                        client.shutdown();
                        return shareInfoPO;
                    }
                }
                nextMarker = objectListing.getNextMarker();

            } while (objectListing.isTruncated());
            client.shutdown();
            return null;
        }else{
            SimplifiedObjectMeta meta = client.getSimplifiedObjectMeta(shareInfoPO.getBucketName(), shareInfoPO.getFileKey());
            shareInfoPO.setSize(FileController.getFileSize2Str(meta.getSize()));
            if (meta.getLastModified().getTime() > shareInfoPO.getCreateTime().getTime()){
                shareInfoPO.setCreateTime(meta.getLastModified());
            }
        }
        return shareInfoPO;
    }

    @Override
    public void deleteByFileKey(String fileKey, String bucket) {
        String sql = "DELETE FROM %s WHERE fileKey LIKE ? AND bucketName = ?";
        shareInfoDao.execute(String.format(sql, shareInfoDao.tableName()), fileKey + "%", bucket);
    }

    @Override
    public List<ShareInfoPO> list(Long uid) {
        List<ShareInfoPO> shareInfoPOList = shareInfoDao.list(Method.where("uid", C.EQ, uid));
        for (ShareInfoPO infoPO: shareInfoPOList){
            String fileName = infoPO.getName();
            int indexOf = fileName.indexOf(".");
            String fa = "fa-file-o";
            if (indexOf == -1){
                fa = "fa-folder-o";
            }else{
                fileName = fileName.substring(indexOf);
                if (!StringUtils.isEmpty(fileName)){
                    switch (fileName.toUpperCase()){
                        case ".ZIP": case ".RAR": case ".7Z": case ".TAR": case ".ISO": case ".IMG":
                            fa = "fa-file-zip-o";
                            break;
                        case ".JPG": case ".JPEG": case ".GIF": case ".PNG": case ".ICO": case ".BMP":
                            fa = "fa-file-image-o";
                            break;
                        case ".TXT": case ".DOC": case ".DOCX": case ".TIF": case ".PDF":
                            fa = "fa-file-word-o";
                            break;
                        case ".JS": case ".JAVA": case ".CPP": case ".XML": case ".HTML": case ".JSON": case ".CSS":
                        case ".DART": case ".BAT": case ".SH": case ".CMD": case "YAML": case "YML":
                            fa = "fa-file-code-o";
                            break;
                        case ".MP3": case ".MP4": case ".RMVB": case ".RM": case ".AVI": case ".3GP": case ".MPEG1-4":
                        case ".MOV": case ".MTV": case ".DAT": case ".WMV": case ".AMV": case ".FLV": case ".DMV":
                            fa = "fa-file-video-o";
                            break;
                        default:
                            fa = "fa-file-o";
                    }
                }
            }
            infoPO.setIcon(fa);
        }
        return shareInfoPOList;
    }

    @Override
    public void deleteById(String fileId, Long uid) {
        shareInfoDao.del(Method.where("id", C.EQ, fileId).and("uid", C.EQ, uid));
    }

    @Override
    public void update(ShareInfoPO info) {
        shareInfoDao.update(info);
    }
}
