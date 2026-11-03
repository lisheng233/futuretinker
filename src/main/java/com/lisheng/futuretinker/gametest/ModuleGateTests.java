package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.config.FutureTinkerConfig;
import com.lisheng.futuretinker.module.FtModule;
import com.lisheng.futuretinker.module.ModuleRegistry;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * M1 的判定：模块闸门接在配置上，而且关掉的模块什么都不注册。
 *
 * <p>跑法：{@code python tools/verify_gametest.py}（原语：{@code gradlew runGameTestServer}，
 * 退出码 = 失败的必需测试数）。证明判定会说「不」：{@code python tools/verify_gametest.py --fault}。</p>
 *
 * <p>三个测试查三个不同的失败模式，不是同一个事实的三遍：</p>
 * <ol>
 *   <li>{@link #gateIsWiredToConfig}——注册阶段没跑到 / 早期读取与 Forge 读取不一致 /
 *       判定说「关」但注册没停；</li>
 *   <li>{@link #moduleIdsAreDeclaredAndUnique}——模块 id 违反契约（大写、重复、没有配置开关）；</li>
 *   <li>{@link #faultProbe}——故障探针，只在 {@code -PftFault} 下失败。</li>
 * </ol>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class ModuleGateTests {
    private ModuleGateTests() {}

    /** 关掉的模块必须什么都没注册；启用的模块必须真的注册了那个标签页。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void gateIsWiredToConfig(GameTestHelper helper) {
        for (FtModule module : ModuleRegistry.modules()) {
            String id = module.id();

            Optional<Boolean> gated = ModuleRegistry.gatedState(id);
            helper.assertTrue(gated.isPresent(),
                    "模块 " + id + " 的开关从未在注册阶段被读取：RegisterEvent 监听器没跑到该模块");

            // 早期读取（构造阶段读文件）与 Forge 自己加载的配置必须给出同一个答案。
            // 这条断言抓的是「手写读取器路径写错/文件读错」——它不会自己暴露，只会静默按默认值走。
            Boolean early = FutureTinkerConfig.earlySwitches().get(id);
            helper.assertTrue(early != null,
                    "模块 " + id + " 没有启动期开关快照：bootstrap() 没跑到或该模块没有配置");
            boolean configuredNow = FutureTinkerConfig.isModuleEnabled(id);
            helper.assertTrue(Boolean.valueOf(configuredNow).equals(early),
                    "模块 " + id + " 启动期从文件读到 " + early + "，Forge 加载配置后是 " + configuredNow
                            + "：两条读取路径不一致（键路径写错、文件读错，或读的不是同一个文件）");

            boolean gatedOn = gated.orElse(false);
            helper.assertTrue(gatedOn == configuredNow,
                    "模块 " + id + " 闸门读到 " + gatedOn + "，配置是 " + configuredNow + "：闸门没接在配置上");

            ResourceLocation tabId = FutureTinker.id(id);
            boolean tabPresent = BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabId);
            helper.assertTrue(tabPresent == configuredNow,
                    "模块 " + id + " 配置 enabled=" + configuredNow
                            + "，但创造模式标签页 " + tabId + " 存在=" + tabPresent
                            + "：关掉的模块必须什么都没注册");
        }
        helper.succeed();
    }

    /** 模块 id 的静态契约：小写、唯一、且有配置开关（否则这个模块关不掉）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void moduleIdsAreDeclaredAndUnique(GameTestHelper helper) {
        Set<String> seen = new HashSet<>();
        for (FtModule module : ModuleRegistry.modules()) {
            String id = module.id();
            helper.assertTrue(!id.isBlank() && id.equals(id.toLowerCase(Locale.ROOT)),
                    "模块 id 必须是小写非空: '" + id + "'");
            helper.assertTrue(seen.add(id), "模块 id 重复: '" + id + "'");
            helper.assertTrue(FutureTinkerConfig.declaredModules().contains(id),
                    "模块 '" + id + "' 没有配置开关——关不掉的内容不符合「模块可独立启停」");
        }
        helper.succeed();
    }

    /**
     * 故障探针：仅当带 {@code -PftFault}（= 系统属性 futuretinker.selftest.fault）时失败。
     * 它存在的唯一目的是证明这套判定会说「不」——一条永远通过的测试不算检查。
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void faultProbe(GameTestHelper helper) {
        if (Boolean.getBoolean("futuretinker.selftest.fault")) {
            helper.fail("注入故障：这条必需测试被要求失败（-PftFault），退出码必须非 0");
        }
        helper.succeed();
    }
}
