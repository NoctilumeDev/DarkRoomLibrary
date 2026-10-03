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
