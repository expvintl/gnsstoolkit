# planet-validate

星空图天体位置的地面真值工具。

- `regen.py`：把 `data/MoonCalculator.kt` 与 `data/PlanetPositions.kt` 剥成纯 JVM 源码（去掉 `@param:StringRes`、`R.string.*`、androidx 导入）拼成 `Bench.kt`。**改了这两个文件必须重跑。**
- `run-bench.sh`：先跑 `regen.py`，再用 kotlinc-embeddable 编成 `bench.jar` 并运行，输出 6 组（时刻 × 经纬度）× 9 个天体的 JSON。
- `compare.py`：用 **skyfield + JPL DE421 星历**（`.toolchain/pylibs` 与 `.toolchain/de421.bsp`）算同一时刻的方位/高度角，与 Kotlin 输出逐条比对。判定阈值 0.15°。

当前结果：54 行全部通过，最差 0.081°（土星，木星以外用质心轨道根数带来的固有偏差），其余 ≤0.03°，月球 0.017°。

运行：

```bash
export JAVA_HOME=/sd/Ubuntu/opt/jdks/jdk21
bash run-bench.sh 2>/dev/null | grep '^{' > bench-out.jsonl
PYTHONPATH=/sd/Ubuntu/workspace/default/.toolchain/pylibs python3 compare.py
```
