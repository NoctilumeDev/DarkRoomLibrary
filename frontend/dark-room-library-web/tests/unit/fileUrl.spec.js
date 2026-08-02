import { describe, expect, it } from "vitest";
import {
  API_BASE_URL,
  buildApiUrl,
  resolveFileUrl,
  toApiRequestPath,
} from "../../src/utils/fileUrl.js";

describe("file URL helpers", () => {
  it("builds API URLs without duplicating separators", () => {
    expect(buildApiUrl("/user/auth")).toBe(`${API_BASE_URL}/user/auth`);
    expect(buildApiUrl("user/auth")).toBe(`${API_BASE_URL}/user/auth`);
    expect(buildApiUrl("https://cdn.example.test/cover.webp")).toBe(
      "https://cdn.example.test/cover.webp"
    );
  });

  it("normalizes stored file locations while preserving safe absolute sources", () => {
    expect(resolveFileUrl("")).toBe("");
    expect(resolveFileUrl("upload/pic/cover.webp")).toBe("/upload/pic/cover.webp");
    expect(resolveFileUrl("/upload/pic/cover.webp")).toBe("/upload/pic/cover.webp");
    expect(resolveFileUrl("data:image/png;base64,AAAA")).toBe(
      "data:image/png;base64,AAAA"
    );
    expect(resolveFileUrl("blob:https://library.example.test/id")).toBe(
      "blob:https://library.example.test/id"
    );
    expect(resolveFileUrl("https://cdn.example.test/cover.webp")).toBe(
      "https://cdn.example.test/cover.webp"
    );
  });

  it("converts same-origin API URLs back to Axios request paths", () => {
    expect(toApiRequestPath("")).toBe("");
    expect(
      toApiRequestPath(`${API_BASE_URL}/file/public?fileName=cover.webp`)
    ).toBe("/file/public?fileName=cover.webp");
    expect(toApiRequestPath(`${API_BASE_URL}`)).toBe("/");
    expect(toApiRequestPath("/upload/pic/cover.webp?size=small")).toBe(
      "/upload/pic/cover.webp?size=small"
    );
  });
});
