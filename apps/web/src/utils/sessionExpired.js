import { createApp, ref, h } from 'vue';
import ConfirmDialog from '../components/ui/ConfirmDialog.vue';

// Singleton to prevent multiple dialogs
let isShowing = false;

export function showSessionExpiredDialog() {
    if (isShowing) return;
    isShowing = true;

    const container = document.createElement('div');
    document.body.appendChild(container);

    // Create a tiny wrapper app that mounts ConfirmDialog and calls confirm()
    const app = createApp({
        setup() {
            const dialogRef = ref(null);

            // Use nextTick-like approach: call confirm() after mount
            const onMounted = async () => {
                await new Promise(r => setTimeout(r, 50)); // wait for DOM
                if (dialogRef.value) {
                    await dialogRef.value.confirm({
                        title: '登录已过期',
                        message: '您的登录凭证已失效（可能是因为长时间未操作或系统更新）。\n请重新登录以继续使用。',
                        confirmText: '重新登录',
                        cancelText: '重新登录',
                        type: 'danger'
                    });
                }
                // Whether confirmed or cancelled, always redirect
                cleanup();
            };

            const cleanup = () => {
                localStorage.removeItem('token');
                localStorage.removeItem('refreshToken');
                localStorage.removeItem('user');
                app.unmount();
                document.body.removeChild(container);
                isShowing = false;
                window.location.href = '/login';
            };

            // Trigger after render
            setTimeout(onMounted, 0);

            return () => h(ConfirmDialog, { ref: dialogRef });
        }
    });

    app.mount(container);
}
