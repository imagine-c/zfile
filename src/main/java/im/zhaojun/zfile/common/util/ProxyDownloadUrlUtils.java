package im.zhaojun.zfile.common.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import cn.hutool.extra.spring.SpringUtil;
import im.zhaojun.zfile.admin.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;

/**
 * 代理下载链接工具类
 *
 * @author zhaojun
 */
@Slf4j
public class ProxyDownloadUrlUtils {

	private static SystemConfigService systemConfigService;


	private static final String PROXY_DOWNLOAD_LINK_DELIMITER= ":";


	/**
	 * 服务器代理下载 URL 有效期 (分钟).
	 */
	public static final Integer PROXY_DOWNLOAD_LINK_EFFECTIVE_SECOND = 1800;

	public static String generatorSignature(Integer storageId, String pathAndName, Integer effectiveSecond) {
		if (systemConfigService == null) {
			systemConfigService = SpringUtil.getBean(SystemConfigService.class);
		}

		// 如果有效时间为空, 则设置 30 分钟过期
		if (effectiveSecond == null || effectiveSecond < 1) {
			effectiveSecond = PROXY_DOWNLOAD_LINK_EFFECTIVE_SECOND;
		}

		// 过期时间的秒数
		long second = DateUtil.offsetSecond(DateUtil.date(), effectiveSecond).getTime();
		String content = storageId + PROXY_DOWNLOAD_LINK_DELIMITER + pathAndName + PROXY_DOWNLOAD_LINK_DELIMITER + second;

		String rsaHexKey = systemConfigService.getRsaHexKey();
		byte[] key = HexUtil.decodeHex(rsaHexKey);
		//构建
		SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, key);

		//加密
		return aes.encryptHex(content);
	}


	public static boolean validSignatureExpired(Integer expectedStorageId, String expectedPathAndName, String signature) {
		if (systemConfigService == null) {
			systemConfigService = SpringUtil.getBean(SystemConfigService.class);
		}

		String rsaHexKey = systemConfigService.getRsaHexKey();
		byte[] key = HexUtil.decodeHex(rsaHexKey);
		SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, key);

		try {
			//解密
			String decryptStr = aes.decryptStr(signature);
			List<String> split = StrUtil.split(decryptStr, PROXY_DOWNLOAD_LINK_DELIMITER);
			String storageId = split.get(0);
			String pathAndName = split.get(1);
			String expiredSecond = split.get(2);
			
			// 校验存储源 ID 和文件路径及是否过期.
			if (StrUtil.equals(storageId, Convert.toStr(expectedStorageId))
				&& StrUtil.equals(StringUtils.concat(pathAndName), StringUtils.concat(expectedPathAndName))
				&& new Date().getTime() < Convert.toLong(expiredSecond)) {
				return true;
			}
			
			log.debug("校验链接已过期或不匹配, storageId={}, pathAndName={}, expiredSecond={}, now:={}", storageId, pathAndName, expiredSecond, new Date().getTime());
		} catch (Exception e) {
			log.error("校验链接是否过期异常", e);
			return false;
		}

		return false;
	}

}