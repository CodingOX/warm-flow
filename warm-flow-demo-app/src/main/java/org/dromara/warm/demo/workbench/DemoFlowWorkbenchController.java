package org.dromara.warm.demo.workbench;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * demo 工作台入口，交给静态页渲染。
 */
@Controller
public class DemoFlowWorkbenchController {

    @GetMapping("/demo/workbench")
    public String workbench() {
        return "forward:/demo-workbench.html";
    }
}
