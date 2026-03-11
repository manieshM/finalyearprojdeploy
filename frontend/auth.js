const adminForm = document.getElementById("adminForm");
const adminMessage = document.getElementById("adminMessage");
const authForm = document.getElementById("authForm");
const authMessage = document.getElementById("authMessage");
const signupForm = document.getElementById("signupForm");
const signupMessage = document.getElementById("signupMessage");
const forgotForm = document.getElementById("forgotForm");
const forgotMessage = document.getElementById("forgotMessage");

if (localStorage.getItem("mpr_token")) {
  window.location.replace("/app.html");
}

async function login(email, password) {
  const response = await fetch("/api/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password })
  });
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || "Login failed");
  }

  localStorage.setItem("mpr_token", data.token);
  localStorage.setItem("mpr_user", data.user.email);
  localStorage.setItem("mpr_role", data.user.role);
  localStorage.setItem("mpr_can_case_edit", data.user.canCaseEdit ? "1" : "0");
  window.location.replace("/app.html");
}

adminForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  adminMessage.textContent = "";
  const formData = new FormData(adminForm);

  try {
    await login(formData.get("adminId"), formData.get("password"));
  } catch (error) {
    adminMessage.textContent = error.message;
    adminMessage.className = "message danger";
  }
});

authForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  authMessage.textContent = "";
  const formData = new FormData(authForm);

  try {
    await login(formData.get("email"), formData.get("password"));
  } catch (error) {
    authMessage.textContent = error.message;
    authMessage.className = "message danger";
  }
});

signupForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  signupMessage.textContent = "";
  const formData = new FormData(signupForm);

  try {
    const response = await fetch("/api/signup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: formData.get("email"),
        password: formData.get("password")
      })
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "Account creation failed");
    }
    signupForm.reset();
    signupMessage.textContent = `Account created for ${data.user.email}. Sign in now.`;
    signupMessage.className = "message success";
  } catch (error) {
    signupMessage.textContent = error.message;
    signupMessage.className = "message danger";
  }
});

forgotForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  forgotMessage.textContent = "";
  const formData = new FormData(forgotForm);
  const email = formData.get("email");
  const code = formData.get("code");
  const newPassword = formData.get("newPassword");

  try {
    if (!code || !newPassword) {
      const response = await fetch("/api/forgot-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email })
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || "Reset request failed");
      }
      forgotMessage.textContent = `Demo reset code: ${data.resetCode}`;
      forgotMessage.className = "message success";
      return;
    }

    const response = await fetch("/api/reset-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, code, newPassword })
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "Password reset failed");
    }
    forgotForm.reset();
    forgotMessage.textContent = "Password reset successful. You can sign in now.";
    forgotMessage.className = "message success";
  } catch (error) {
    forgotMessage.textContent = error.message;
    forgotMessage.className = "message danger";
  }
});
