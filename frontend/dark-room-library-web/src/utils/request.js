import axios from "axios";
import { getToken, clearAuthSession } from "@/utils/storage.js";
import router from "@/router";
import { API_BASE_URL } from "@/utils/fileUrl.js";

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 8000,
});

request.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { status, data } = error.response;
      const authMessage = data && typeof data.msg === "string" ? data.msg : "";
      if (
        status === 401 ||
        data?.code === 401 ||
        authMessage.includes("身份认证") ||
        authMessage.includes("请先登录")
      ) {
        clearAuthSession();
        router.push("/login");
      }
    }
    return Promise.reject(error);
  }
);

export default request;
