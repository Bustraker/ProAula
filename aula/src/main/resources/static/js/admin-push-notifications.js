const statusMessage = document.getElementById("pushStatus");
const enableButton = document.getElementById("enablePush");
const disableButton = document.getElementById("disablePush");
let activeMessaging;

if (statusMessage && enableButton && disableButton && "serviceWorker" in navigator && "Notification" in window) {
  const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]')?.content;

  async function requestJson(url) {
    const response = await fetch(url, { credentials: "same-origin" });
    if (!response.ok) {
      throw new Error(`No se pudo cargar la configuración de notificaciones (${response.status}).`);
    }
    return response.json();
  }

  async function saveToken(url, method, token) {
    const headers = { "Content-Type": "application/json" };
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
    const response = await fetch(url, {
      method,
      credentials: "same-origin",
      headers,
      body: JSON.stringify({ token })
    });
    if (!response.ok) {
      throw new Error(`No se pudo actualizar este dispositivo (${response.status}).`);
    }
  }

  enableButton.addEventListener("click", async () => {
    enableButton.disabled = true;
    statusMessage.textContent = "Conectando las notificaciones...";
    try {
      const config = await requestJson("/api/firebase/config");
      if (!config.pushConfigured) {
        throw new Error("Falta configurar FIREBASE_VAPID_KEY en el servidor.");
      }
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        throw new Error("El navegador no concedió permiso para mostrar notificaciones.");
      }
      const registration = await navigator.serviceWorker.register("/firebase-messaging-sw.js");
      const { initializeApp } = await import("https://www.gstatic.com/firebasejs/13.0.0/firebase-app.js");
      const { getMessaging, getToken } = await import("https://www.gstatic.com/firebasejs/13.0.0/firebase-messaging.js");
      const app = initializeApp({
        apiKey: config.apiKey,
        authDomain: config.authDomain,
        projectId: config.projectId,
        storageBucket: config.storageBucket,
        messagingSenderId: config.messagingSenderId,
        appId: config.appId,
        measurementId: config.measurementId
      });
      const messaging = getMessaging(app);
      activeMessaging = messaging;
      const token = await getToken(messaging, {
        vapidKey: config.vapidKey,
        serviceWorkerRegistration: registration
      });
      if (!token) throw new Error("Firebase no devolvió un token para este dispositivo.");
      await saveToken("/api/admin/push-subscriptions", "POST", token);
      disableButton.dataset.token = token;
      disableButton.hidden = false;
      statusMessage.textContent = "Este dispositivo recibirá notificaciones de solicitudes ADMIN.";
    } catch (error) {
      statusMessage.textContent = error instanceof Error ? error.message : "No se pudieron activar las notificaciones.";
    } finally {
      enableButton.disabled = false;
    }
  });

  disableButton.addEventListener("click", async () => {
    const token = disableButton.dataset.token;
    if (!token) return;
    disableButton.disabled = true;
    try {
      if (activeMessaging) {
        const { deleteToken } = await import("https://www.gstatic.com/firebasejs/13.0.0/firebase-messaging.js");
        await deleteToken(activeMessaging);
      }
      await saveToken("/api/admin/push-subscriptions", "DELETE", token);
      disableButton.hidden = true;
      statusMessage.textContent = "Se desactivaron las notificaciones para este dispositivo.";
    } catch (error) {
      statusMessage.textContent = error instanceof Error ? error.message : "No se pudieron desactivar las notificaciones.";
    } finally {
      disableButton.disabled = false;
    }
  });
}
