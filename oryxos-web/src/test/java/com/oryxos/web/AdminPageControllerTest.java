package com.oryxos.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理台入口跳转契约（contracts/admin-rest-api.md 静态页契约）：
 * /admin 必须 302 到真实静态资源 /admin/index.html，
 * 而不是落到 NoResourceFoundException 的 JSON 信封。
 */
class AdminPageControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminPageController()).build();
    }

    @Test
    void adminRedirectsToStaticIndex() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/index.html"));
    }
}
