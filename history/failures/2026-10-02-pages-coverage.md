# Pages 覆盖率门禁失败

- 对象：`d6e42a81d3eb83ed483553b7319bbe55dceb8c7f`，北京时间 2026-10-02 的主线发布。
- 原始记录：[GitHub Pages run 36928097298](https://github.com/NoctilumeDev/DarkRoomLibrary/actions/runs/36928097298)。
- 失败步骤：`Match backend coverage to the public baseline`；部署未执行，线上继续保留上一版本。
- 原始门禁输出：

```text
Backend covered lines must be 3996, found 3994
Backend displayed line coverage must be 73.28%, found 73.24%
```

这是 CI 实测与已记录本地基线的差异，不能直接把某一组数字改成 PASS。
后续修复与新验证记录见 [验证报告](../../docs/verification-report.md)，本记录保留首次失败对象，
不把后续通过回填为原提交通过。

## 2026-10-03 的受控核对

验证码 `generate()` 从三种运算中随机选择，原测试也依赖真实随机数。将现有三项单测的
运算固定为 `0/1/1` 时，三项测试通过，但 `CaptchaServiceImpl.java` 的乘法分支第 46、47 行
未执行，该文件覆盖为 `37/45`；改为 `0/1/2` 后仍是三项测试通过，覆盖为 `39/45`，新增的
恰好是这两行。正式修复只在测试中控制随机源并核对表达式和答案，生产生成器保持不变。

该对照证明存在能产生两行差异的随机覆盖缺口。原始 Pages run 未留存逐行 XML，
因此不能断言它是该次失败的唯一原因。后续 CI/Pages 留存 JaCoCo XML，70% 阈值与
原有 `3996/5453` 基线均保留；当前通过与发布事实另见验证报告。
