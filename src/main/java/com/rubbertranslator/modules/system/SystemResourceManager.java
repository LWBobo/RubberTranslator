package com.rubbertranslator.modules.system;

import com.google.gson.Gson;
import com.rubbertranslator.modules.TranslatorFacade;
import com.rubbertranslator.modules.filter.ProcessFilter;
import com.rubbertranslator.modules.history.TranslationHistory;
import com.rubbertranslator.modules.system.proxy.*;
import com.rubbertranslator.modules.textinput.clipboard.ClipBoardListenerThread;
import com.rubbertranslator.modules.textinput.mousecopy.DragCopyThread;
import com.rubbertranslator.modules.textinput.ocr.OCRUtils;
import com.rubbertranslator.modules.textprocessor.post.TextPostProcessor;
import com.rubbertranslator.modules.textprocessor.pre.TextPreProcessor;
import com.rubbertranslator.modules.translate.TranslatorFactory;
import com.rubbertranslator.modules.translate.TranslatorType;
import com.rubbertranslator.modules.translate.baidu.BaiduTranslator;
import com.rubbertranslator.modules.translate.youdao.YoudaoTranslator;
import net.sf.cglib.proxy.Enhancer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * @author Raven
 * @version 1.0
 * @date 2020/5/10 22:25
 * 系统资源管理
 */
public class SystemResourceManager {

    public static String configJsonPath = "./configuration.json";
    private static ClipBoardListenerThread clipBoardListenerThread;
    private static DragCopyThread dragCopyThread;
    private static TranslatorFacade facade;

    // 所有用户得操作，都应该通过configurationProxy来操作
    private static SystemConfiguration configurationProxy;


    // 只能通过静态方法调用此类
    private SystemResourceManager() {
    }


    public static ClipBoardListenerThread getClipBoardListenerThread() {
        assert clipBoardListenerThread != null : "请先执行SystemResourceManager.init";
        return clipBoardListenerThread;
    }

    public static DragCopyThread getDragCopyThread() {
        assert dragCopyThread != null : "请先执行SystemResourceManager.init";
        return dragCopyThread;
    }

    public static TranslatorFacade getFacade() {
        assert facade != null : "请先执行SystemResourceManager.init";
        return facade;
    }

    public static SystemConfiguration getConfigurationProxy() {
        return configurationProxy;
    }


    /**
     * 初始化系统资源
     *
     * @return true 初始化成功
     * false 初始化失败
     */
    public static boolean init() {
        // 1. 加载配置文件
        configurationProxy = loadSystemConfig();
        Logger.getLogger(SystemResourceManager.class.getName()).info(configurationProxy.toString());
        if (configurationProxy == null) return false;
        // 2.初始化facade
        facade = new TranslatorFacade();
        // 3. 启动各组件
        // ui配置在controller进行应用
        return textInputInit(configurationProxy.getTextInputConfig()) &&
                filterInit(configurationProxy.getProcessFilterConfig()) &&
                preTextProcessInit(configurationProxy.getTextProcessConfig().getTextPreProcessConfig()) &&
                postTextProcessInit(configurationProxy.getTextProcessConfig().getTextPostProcessConfig()) &&
                translatorInit(configurationProxy.getTranslatorConfig()) &&
                historyInit(configurationProxy.getHistoryConfig());
    }


    /**
     * 释放资源
     */
    public static void destroy() {
        textInputDestroy();
        // 其余模块没有资源需要手动释放
        System.runFinalization();
        System.exit(0);
    }


    /**
     * 加载配置文件
     *
     * @return null 加载失败
     * 配置类 加载成功
     */
    private static SystemConfiguration loadSystemConfig() {
        // 加载本地目录
        File file = new File(configJsonPath);
        Path path;
        String configJson;
        try {
            if (file.exists()) {
                path = Paths.get(file.toURI());
            } else {
                // 不存在加载默认配置文件
                path = Paths.get(SystemConfiguration.class.getResource("/config/default_configuration.json").toURI());

            }
            configJson = Files.readString(path);
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
            return null;
        }
        // json --> object
        Gson gson = new Gson();
        // 原始配置记录 TODO：这种方式耦合度很高，下面得wrapper中引用了configuration中的对象。
        SystemConfiguration configuration = gson.fromJson(configJson, SystemConfiguration.class);
        if (configJson == null) {
            return null;
        } else {
            return wrapProxy(configuration);
        }
    }

    /**
     * 为所有系统配置包装代理
     * 每当用户修改任何系统配置，都将配置持久化
     *  两层代理：
     *  1. 静态代理： 设置更改后，通知后续处理模块，立马更改
     *  2. 动态代理： 设置更改后，持久化所有设置
     * @param configuration
     */
    private static SystemConfiguration wrapProxy(SystemConfiguration configuration) {
        // 文本输入配置
        SystemConfiguration.TextInputConfig textInputConfigStaticProxy = new TextInputConfigStaticProxy(configuration.getTextInputConfig());
        SystemConfiguration.TextInputConfig textInputConfigProxy = (SystemConfiguration.TextInputConfig)
                Enhancer.create(SystemConfiguration.TextInputConfig.class, new ConfigProxy(textInputConfigStaticProxy));

        // 过滤器
        SystemConfiguration.ProcessFilterConfig processFilterStaticConfig = new ProcessFilterStaticConfig(configuration.getProcessFilterConfig());
        SystemConfiguration.ProcessFilterConfig processFilterConfigProxy = (SystemConfiguration.ProcessFilterConfig)
                Enhancer.create(SystemConfiguration.ProcessFilterConfig.class, new ConfigProxy(processFilterStaticConfig));

        // 前置处理
        SystemConfiguration.TextProcessConfig.TextPreProcessConfig textPreProcessStaticConfig = new TextPreProcessStaticConfig(configuration.getTextProcessConfig().getTextPreProcessConfig());
        SystemConfiguration.TextProcessConfig.TextPreProcessConfig preProcessConfigProxy = (SystemConfiguration.TextProcessConfig.TextPreProcessConfig)
                Enhancer.create(SystemConfiguration.TextProcessConfig.TextPreProcessConfig.class, new ConfigProxy(textPreProcessStaticConfig));
        // 后置处理
        SystemConfiguration.TextProcessConfig.TextPostProcessConfig textPostProcessStaticConfig = new TextPostProcessStaticConfig(configuration.getTextProcessConfig().getTextPostProcessConfig());
        SystemConfiguration.TextProcessConfig.TextPostProcessConfig textPostProcessConfig = (SystemConfiguration.TextProcessConfig.TextPostProcessConfig)
                Enhancer.create(SystemConfiguration.TextProcessConfig.TextPostProcessConfig.class, new ConfigProxy(textPostProcessStaticConfig));

        // 翻译配置
        SystemConfiguration.TranslatorConfig translatorStaticConfig = new TranslatorStaticConfig(configuration.getTranslatorConfig());
        SystemConfiguration.TranslatorConfig translatorConfigProxy = (SystemConfiguration.TranslatorConfig)
                Enhancer.create(SystemConfiguration.TranslatorConfig.class, new ConfigProxy(translatorStaticConfig));

        // 历史配置
        HistoryStaticConfig historyStaticConfig = new HistoryStaticConfig(configuration.getHistoryConfig());
        SystemConfiguration.HistoryConfig historyConfigProxy = (SystemConfiguration.HistoryConfig)
                Enhancer.create(SystemConfiguration.HistoryConfig.class, new ConfigProxy(historyStaticConfig));

        // ui配置
        SystemConfiguration.UIConfig uiConfigProxy = (SystemConfiguration.UIConfig)
                Enhancer.create(SystemConfiguration.UIConfig.class,new ConfigProxy(configuration.getUiConfig()));

        // 注入
        configuration.setTextInputConfig(textInputConfigProxy);
        configuration.setProcessFilterConfig(processFilterConfigProxy);
        configuration.getTextProcessConfig().setTextPreProcessConfig(preProcessConfigProxy);
        configuration.getTextProcessConfig().setTextPostProcessConfig(textPostProcessConfig);
        configuration.setTranslatorConfig(translatorConfigProxy);
        configuration.setHistoryConfig(historyConfigProxy);
        configuration.setUiConfig(uiConfigProxy);
        Logger.getLogger(SystemConfiguration.class.getName()).info("wrap代理完成");
        return configuration;
    }


    private static boolean textInputInit(SystemConfiguration.TextInputConfig configuration) {
        clipBoardListenerThread = new ClipBoardListenerThread();
        clipBoardListenerThread.setRun(configuration.isOpenClipboardListener());
        dragCopyThread = new DragCopyThread();
        dragCopyThread.setRun(configuration.isDragCopy());
        OCRUtils.setApiKey(configuration.getBaiduOcrApiKey());
        OCRUtils.setSecretKey(configuration.getBaiduOcrSecretKey());

        clipBoardListenerThread.start();
        dragCopyThread.start();
        return true;
    }

    private static void textInputDestroy() {
        clipBoardListenerThread.exit();
        dragCopyThread.exit();
    }

    private static boolean filterInit(SystemConfiguration.ProcessFilterConfig configuration) {
        ProcessFilter processFilter = new ProcessFilter();
        processFilter.setOpen(configuration.isOpenProcessFilter());
        processFilter.addFilterList(configuration.getProcessList());
        facade.setProcessFilter(processFilter);
        return true;
    }

    private static boolean preTextProcessInit(SystemConfiguration.TextProcessConfig.TextPreProcessConfig preProcessConfig) {
        TextPreProcessor textPreProcessor = new TextPreProcessor();
        textPreProcessor.setTryToKeepParagraph(preProcessConfig.isTryKeepParagraphFormat());
        facade.setTextPreProcessor(textPreProcessor);
        return true;
    }

    private static boolean postTextProcessInit(SystemConfiguration.TextProcessConfig.TextPostProcessConfig postProcessConfig) {
        TextPostProcessor textPostProcessor = new TextPostProcessor();
        textPostProcessor.setOpen(postProcessConfig.isOpenPostProcess());
        textPostProcessor.getReplacer().setCaseInsensitive(postProcessConfig.getWordsReplacerConfig().isCaseInsensitive());
        textPostProcessor.getReplacer().addWords(postProcessConfig.getWordsReplacerConfig().getWordsMap());
        facade.setTextPostProcessor(textPostProcessor);
        return true;
    }

    private static boolean translatorInit(SystemConfiguration.TranslatorConfig configuration) {
        TranslatorFactory translatorFactory = new TranslatorFactory();
        translatorFactory.setEngineType(configuration.getCurrentTranslator());
        translatorFactory.setSourceLanguage(configuration.getSourceLanguage());
        translatorFactory.setDestLanguage(configuration.getDestLanguage());
        if (configuration.getBaiduTranslatorApiKey() != null && configuration.getBaiduTranslatorSecretKey() != null) {
            BaiduTranslator baiduTranslator = new BaiduTranslator();
            baiduTranslator.setAPP_KEY(configuration.getBaiduTranslatorApiKey());
            baiduTranslator.setSECRET_KEY(configuration.getBaiduTranslatorSecretKey());
            translatorFactory.addTranslator(TranslatorType.BAIDU, baiduTranslator);
        }
        if (configuration.getYouDaoTranslatorApiKey() != null && configuration.getYouDaoTranslatorSecretKey() != null) {
            YoudaoTranslator youdaoTranslator = new YoudaoTranslator();
            youdaoTranslator.setAPP_KEY(configuration.getYouDaoTranslatorApiKey());
            youdaoTranslator.setSECRET_KEY(configuration.getYouDaoTranslatorSecretKey());
            translatorFactory.addTranslator(TranslatorType.YOUDAO, youdaoTranslator);
        }
        facade.setTranslatorFactory(translatorFactory);
        return true;
    }

    public static boolean historyInit(SystemConfiguration.HistoryConfig configuration){
        TranslationHistory history = new TranslationHistory();
        history.setHistoryCapacity(configuration.getHistoryNum());
        return true;
    }


}
