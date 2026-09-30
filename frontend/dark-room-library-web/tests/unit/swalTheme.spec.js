import { resolveSwalThemeClass } from "../../src/utils/swalPlugin.js";

describe("SweetAlert theme resolution", () => {
  afterEach(() => {
    document.body.innerHTML = "";
    delete document.body.dataset.adminTheme;
    delete document.body.dataset.readerTheme;
  });

  test("uses the staff paper theme inside procurement and logistics workbenches", () => {
    document.body.innerHTML = '<div data-staff-theme="paper"></div>';

    expect(resolveSwalThemeClass()).toBe("swal-theme--staff-paper");
  });

  test("keeps existing day and night theme behavior outside staff workbenches", () => {
    document.body.dataset.adminTheme = "day";
    expect(resolveSwalThemeClass()).toBe("swal-theme--day");

    delete document.body.dataset.adminTheme;
    expect(resolveSwalThemeClass()).toBe("swal-theme--night");
  });
});
