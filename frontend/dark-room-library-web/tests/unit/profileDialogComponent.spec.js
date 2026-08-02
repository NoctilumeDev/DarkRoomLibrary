import { shallowMount } from "@vue/test-utils";
import ProfileDialog from "../../src/components/ProfileDialog.vue";

const mocks = vi.hoisted(() => ({
  getToken: vi.fn(() => "token"),
  clearAuthSession: vi.fn(),
}));

vi.mock("@/demo/runtime.js", () => ({
  DEMO_MODE: false,
}));

vi.mock("@/utils/storage", () => ({
  getToken: mocks.getToken,
  clearAuthSession: mocks.clearAuthSession,
}));

describe("ProfileDialog component", () => {
  function mountDialog(axiosPut = vi.fn()) {
    return shallowMount(ProfileDialog, {
      props: {
        modelValue: true,
        userInfo: {
          url: "/avatar.png",
          name: "纸页读者",
          email: "reader@example.com",
          role: 2,
        },
      },
      global: {
        mocks: {
          $axios: { put: axiosPut, post: vi.fn() },
          $message: {
            error: vi.fn(),
            info: vi.fn(),
            success: vi.fn(),
            warning: vi.fn(),
          },
          $router: { push: vi.fn() },
          $swalConfirm: vi.fn(),
        },
      },
    });
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("synchronizes profile fields and rejects an invalid email locally", async () => {
    const wrapper = mountDialog();

    expect(wrapper.vm.form).toEqual({
      url: "/avatar.png",
      name: "纸页读者",
      email: "reader@example.com",
    });

    wrapper.vm.form.email = "invalid-email";
    await wrapper.vm.save();

    expect(wrapper.vm.$message.warning).toHaveBeenCalledWith("邮箱格式不正确。");
    expect(wrapper.vm.$axios.put).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("submits an unchanged email without requesting a verification code", async () => {
    const axiosPut = vi.fn().mockResolvedValue({
      data: { code: 200, msg: "个人资料已更新。" },
    });
    const wrapper = mountDialog(axiosPut);
    wrapper.vm.form.name = "夜航读者";

    await wrapper.vm.save();

    expect(axiosPut).toHaveBeenCalledWith("/user/update", {
      userAvatar: "/avatar.png",
      userName: "夜航读者",
      userEmail: "reader@example.com",
      verificationCode: undefined,
    });
    expect(wrapper.emitted("saved")).toHaveLength(1);
    expect(wrapper.emitted("update:modelValue")).toContainEqual([false]);
    wrapper.unmount();
  });
});
