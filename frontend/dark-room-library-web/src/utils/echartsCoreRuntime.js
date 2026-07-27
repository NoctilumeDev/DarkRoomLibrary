import { init, use } from "echarts/core";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

use([GridComponent, TooltipComponent, CanvasRenderer]);

export { init, use };
