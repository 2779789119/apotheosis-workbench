# libs

第三方模组 jar 有版权，不入库。编译前把下面两个文件放进本目录（版本需与 `build.gradle` 中一致）：

- `Apotheosis-1.20.1-7.4.8.jar`
- `Placebo-1.20.1-8.6.3.jar`

来源：CurseForge / Modrinth 下载对应版本的模组 jar，直接改名不用解压。缺少这两个 jar 时编译会报找不到 `dev.shadowsoffire.apotheosis.affix.Affix`。
