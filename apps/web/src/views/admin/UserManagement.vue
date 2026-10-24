<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, onMounted, computed } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const users = ref([]);
const loading = ref(false);
const error = ref('');
const toast = ref(null);
const userQuery = ref('');
const roleFilter = ref('all');

// Confirmation Dialog State
const confirmDialog = ref(null);

const fetchUsers = async () => {
  loading.value = true;
  try {
    const res = await admin.getUsers();
    users.value = res.data;
  } catch (err) {
    error.value = err.response?.data?.message || '获取用户列表失败';
  } finally {
    loading.value = false;
  }
};

const filteredUsers = computed(() => {
  const q = userQuery.value.trim().toLowerCase();
  return users.value.filter(user => {
    const matchesRole = roleFilter.value === 'all' || user.role === roleFilter.value;
    const matchesQuery = !q || user.username.toLowerCase().includes(q) || String(user.id).includes(q);
    return matchesRole && matchesQuery;
  });
});
const adminCount = computed(() => users.value.filter(user => user.role === 'admin').length);
const memberCount = computed(() => users.value.filter(user => user.subscription_end_date && new Date(user.subscription_end_date) > new Date()).length);
const isMember = (user) => Boolean(user.subscription_end_date && new Date(user.subscription_end_date) > new Date());

const updateUserRole = async (user, newRole) => {
  const confirmed = await confirmDialog.value.confirm({
    title: "确认修改角色",
    message: `确认将用户 ${user.username} 的角色修改为 ${newRole} 吗？`,
    type: "warning",
    confirmText: "确认修改",
    cancelText: "取消"
  });
  if (!confirmed) return;
  try {
    await admin.updateUserRole(user.id, newRole);
    user.role = newRole;
    toast.value?.add('修改成功', 'success');
  } catch (err) {
    toast.value?.add(err.response?.data?.message || '修改失败', 'error');
  }
};

const deleteUser = async (id) => {
  const confirmed = await confirmDialog.value.confirm({
    title: "确认删除用户",
    message: '确认删除该用户吗？此操作不可恢复！',
    type: "danger",
    confirmText: "确认删除",
    cancelText: "取消"
  });
  if (!confirmed) return;
  try {
    await admin.deleteUser(id);
    users.value = users.value.filter(u => u.id !== id);
    toast.value?.add('删除成功', 'success');
    fetchUsers(); // Refresh list to be sure
  } catch (err) {
    toast.value?.add(err.response?.data?.message || '删除失败', 'error');
  }
};

// --- Add / Edit User ---
const showUserModal = ref(false);
const editingUser = ref(null);
const userForm = ref({ username: '', password: '', role: 'user', subscription_end_date: '' });

const openAddUser = () => {
    editingUser.value = null;
    userForm.value = { username: '', password: '', role: 'user', subscription_end_date: '' };
    showUserModal.value = true;
};

const openEditUser = (user) => {
    editingUser.value = user;
    // Format date for datetime-local input
    let formattedDate = '';
    if (user.subscription_end_date) {
        const d = new Date(user.subscription_end_date);
        d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
        formattedDate = d.toISOString().slice(0, 16);
    }
    // Don't fill password
    userForm.value = { username: user.username, password: '', role: user.role, subscription_end_date: formattedDate };
    showUserModal.value = true;
};

const handleSaveUser = async () => {
  try {
    if (editingUser.value) {
        // Update
        const updates = {};
        if (userForm.value.username !== editingUser.value.username) updates.username = userForm.value.username;
        if (userForm.value.password) updates.password = userForm.value.password;
        if (userForm.value.role !== editingUser.value.role) updates.role = userForm.value.role;

        // Format ISO date to MySQL format (YYYY-MM-DD HH:mm:ss)
        if (userForm.value.subscription_end_date) {
            const date = new Date(userForm.value.subscription_end_date);
            updates.subscription_end_date = date.toISOString().slice(0, 19).replace('T', ' ');
        } else {
            updates.subscription_end_date = null;
        }

        await admin.updateUserDetails(editingUser.value.id, updates);
        toast.value?.add('用户更新成功', 'success');
    } else {
        // Create
        if (!userForm.value.username || !userForm.value.password) {
            toast.value?.add('用户名和密码不能为空', 'warning');
            return;
        }

        const dataToSave = { ...userForm.value };
        if (dataToSave.subscription_end_date) {
            const date = new Date(dataToSave.subscription_end_date);
            dataToSave.subscription_end_date = date.toISOString().slice(0, 19).replace('T', ' ');
        } else {
            dataToSave.subscription_end_date = null;
        }

        await admin.createUser(dataToSave);
        toast.value?.add('用户创建成功', 'success');
    }
    showUserModal.value = false;
    fetchUsers();
  } catch (err) {
    toast.value?.add(err.response?.data?.message || '保存失败', 'error');
  }
};

onMounted(() => {
    fetchUsers();
});
</script>

<template>
  <div class="admin-workspace ui-page admin-stack">
    <div class="admin-page-bar">
      <AdminPageHeading title="用户管理" description="管理账号、角色与会员状态。" />
      <div class="admin-page-actions">
        <button class="ui-btn ui-btn-primary admin-control" type="button" @click="openAddUser">新增用户</button>
      </div>
    </div>

    <div class="admin-metric-strip">
      <div class="admin-metric"><span>账户总数</span><strong>{{ users.length }}</strong></div>
      <div class="admin-metric"><span>管理员</span><strong>{{ adminCount }}</strong></div>
      <div class="admin-metric"><span>Plus 会员</span><strong>{{ memberCount }}</strong></div>
      <div class="admin-metric"><span>当前筛选</span><strong>{{ filteredUsers.length }}</strong></div>
    </div>

    <div class="admin-filter-bar">
      <div class="ui-search admin-search">
        <svg class="ui-search-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
        <input v-model="userQuery" class="ui-input" type="search" placeholder="搜索用户名或 UID" aria-label="搜索用户" />
      </div>
      <div class="admin-segmented" role="group" aria-label="按角色筛选">
        <button type="button" :class="{ 'is-active': roleFilter === 'all' }" @click="roleFilter = 'all'">全部</button>
        <button type="button" :class="{ 'is-active': roleFilter === 'admin' }" @click="roleFilter = 'admin'">管理员</button>
        <button type="button" :class="{ 'is-active': roleFilter === 'user' }" @click="roleFilter = 'user'">普通用户</button>
        <button type="button" :class="{ 'is-active': roleFilter === 'demo' }" @click="roleFilter = 'demo'">展示用户</button>
      </div>
    </div>

    <div v-if="loading" class="admin-table-card">
      <div class="admin-empty">
        <span class="ui-spinner"></span>
        <span class="admin-empty-title">正在加载用户</span>
      </div>
    </div>

    <div v-else-if="error" class="admin-table-card">
      <div class="admin-empty">
        <span class="admin-empty-title">无法读取用户列表</span>
        <span class="admin-empty-copy">{{ error }}</span>
        <button class="ui-btn ui-btn-outline ui-btn-sm mt-3" type="button" @click="fetchUsers">重试</button>
      </div>
    </div>

    <template v-else>
      <div class="admin-table-card hidden lg:block">
        <table class="admin-data-table w-full text-left">
          <thead>
            <tr>
              <th>用户</th>
              <th>UID</th>
              <th>角色</th>
              <th>会员</th>
              <th>注册时间</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in filteredUsers" :key="user.id">
              <td>
                <div class="admin-user-cell">
                  <span class="admin-avatar">{{ user.username.charAt(0).toUpperCase() }}</span>
                  <strong>{{ user.username }}</strong>
                </div>
              </td>
              <td class="font-mono text-[11px]" style="color:var(--text-quote)">#{{ user.id }}</td>
              <td>
                <span class="admin-role" :class="{ 'is-admin': user.role === 'admin', 'is-demo': user.role === 'demo' }">
                  {{ user.role === 'admin' ? '管理员' : user.role === 'demo' ? '展示用户' : '普通用户' }}
                </span>
              </td>
              <td>
                <span v-if="isMember(user)" class="admin-status is-plus">至 {{ new Date(user.subscription_end_date).toLocaleDateString() }}</span>
                <span v-else class="admin-status">未订阅</span>
              </td>
              <td class="text-[11px]" style="color:var(--text-sub)">{{ new Date(user.created_at).toLocaleDateString() }}</td>
              <td class="text-right">
                <div class="admin-row-actions justify-end">
                  <button class="admin-text-action" type="button" @click="openEditUser(user)">编辑</button>
                  <button class="admin-text-action danger" type="button" @click="deleteUser(user.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredUsers.length">
              <td colspan="6">
                <div class="admin-empty admin-table-empty">
                  <span class="admin-empty-title">没有匹配的账号</span>
                  <span class="admin-empty-copy">调整搜索词或角色筛选后再试</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-table-card admin-mobile-list lg:hidden">
        <div v-for="user in filteredUsers" :key="user.id" class="admin-mobile-row">
          <div class="admin-mobile-main">
            <span class="admin-avatar">{{ user.username.charAt(0).toUpperCase() }}</span>
            <div class="admin-mobile-identity">
              <strong>{{ user.username }}</strong>
              <span>#{{ user.id }} · {{ user.role === 'admin' ? '管理员' : user.role === 'demo' ? '展示用户' : '普通用户' }}</span>
            </div>
            <span v-if="isMember(user)" class="admin-status is-plus">Plus</span>
          </div>
          <div class="admin-mobile-meta">
            <span>{{ isMember(user) ? '至 ' + new Date(user.subscription_end_date).toLocaleDateString() : '非会员' }} · 注册于 {{ new Date(user.created_at).toLocaleDateString() }}</span>
            <div class="admin-row-actions">
              <button class="admin-text-action" type="button" @click="openEditUser(user)">编辑</button>
              <button class="admin-text-action danger" type="button" @click="deleteUser(user.id)">删除</button>
            </div>
          </div>
        </div>
        <div v-if="!filteredUsers.length" class="admin-empty admin-table-empty">
          <span class="admin-empty-title">没有匹配的账号</span>
          <span class="admin-empty-copy">调整搜索词或角色筛选后再试</span>
        </div>
      </div>
    </template>

    <div v-if="showUserModal" class="admin-modal-backdrop" @click.self="showUserModal = false">
      <div class="admin-modal" role="dialog" aria-modal="true" aria-label="用户信息">
        <div class="admin-modal-head">
          <h2>{{ editingUser ? '编辑用户' : '新增用户' }}</h2>
          <button class="admin-modal-close" type="button" aria-label="关闭" @click="showUserModal = false">×</button>
        </div>
        <div class="admin-modal-body">
          <div class="admin-form-row">
            <label class="admin-form-label" for="user-name">用户名</label>
            <input id="user-name" v-model="userForm.username" class="ui-input" type="text" autocomplete="off" />
          </div>
          <div class="admin-form-row">
            <label class="admin-form-label" for="user-password">密码 <span class="admin-form-hint">{{ editingUser ? '留空则不修改' : '' }}</span></label>
            <input id="user-password" v-model="userForm.password" class="ui-input" type="password" autocomplete="new-password" />
          </div>
          <div class="admin-form-row">
            <label class="admin-form-label" for="user-role">角色权限</label>
            <select id="user-role" v-model="userForm.role" class="ui-select">
              <option value="user">普通用户</option>
              <option value="demo">展示用户</option>
              <option value="admin">管理员</option>
            </select>
          </div>
          <div v-if="editingUser" class="admin-form-row">
            <label class="admin-form-label" for="user-subscription">Plus 订阅到期时间</label>
            <input id="user-subscription" v-model="userForm.subscription_end_date" class="ui-input" type="datetime-local" />
            <p class="admin-form-hint">置空即取消订阅。</p>
          </div>
        </div>
        <div class="admin-modal-foot">
          <button class="ui-btn ui-btn-outline" type="button" @click="showUserModal = false">取消</button>
          <button class="ui-btn ui-btn-primary" type="button" @click="handleSaveUser">保存</button>
        </div>
      </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>
