import { flushPromises, shallowMount } from "@vue/test-utils";
import Login from "../../src/views/login/Login.vue";

const mocks = vi.hoisted(() => ({
  requestGet: vi.fn(),
  getToken: vi.fn(() => null),
  clearAuthSession: vi.fn(),
  setToken: vi.fn(),
  toggleReaderTheme: vi.fn(() => "day"),
}));

vi.mock("@/demo/runtime.js", () => ({
  activateDemoIdentity: vi.fn(),
  DEMO_IDENTITIES: [],
  DEMO_MODE: false,
}));

vi.mock("@/utils/request.js", () => ({
  default: {
    get: mocks.requestGet,
    post: vi.fn(),
  },
}));

vi.mock("@/utils/storage.js", () => ({
  getToken: mocks.getToken,
  clearAuthSession: mocks.clearAuthSession,
  setToken: mocks.setToken,
}));

vi.mock("@/utils/readerTheme.js", () => ({
  getReaderTheme: vi.fn(() => "night"),
  toggleReaderTheme: mocks.toggleReaderTheme,
}));

describe("Login component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mocks.requestGet.mockResolvedValue({
      data: {
        code: 200,
        data: { captchaId: "captcha-1", expression: "2 + 3" },
      },
    });
    global.requestAnimationFrame = vi.fn(() => 1);
    global.cancelAnimationFrame = vi.fn();
  });

  it("loads a captcha and moves from the threshold into the login sheet", async () => {
    const wrapper = shallowMount(Login, {
      global: {
        mocks: {
          $axios: { get: vi.fn() },
          $message: { error: vi.fn() },
          $router: { push: vi.fn() },
          $swal: { fire: vi.fn() },
        },
      },
    });

    await flushPromises();
    expect(mocks.requestGet).toHaveBeenCalledWith("/captcha/generate");
    expect(wrapper.text()).toContain("循光而入");

    await wrapper.get(".threshold-entry").trigger("click");

    expect(wrapper.find(".auth-card").exists()).toBe(true);
    expect(sessionStorage.getItem("auth-intro-seen")).toBe("1");
    wrapper.unmount();
  });
});
