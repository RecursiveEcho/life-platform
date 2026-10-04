<script setup>
import { ref, onMounted } from 'vue';
import { Search, MapPin, ArrowLeft, ArrowRight, X, UserRound, Ticket, Compass, RefreshCw, LogOut, Star } from 'lucide-vue-next';
import { request } from './api';
const page = ref('discover'), shops = ref({records:[],current:1,pages:0}), orders = ref({records:[],current:1,pages:0});
const shop = ref(null), vouchers = ref({records:[],current:1,pages:0}), reviews = ref({records:[],current:1,pages:0});
const keyword = ref(''), typeId = ref(''), busy = ref(false), error = ref(''), notice = ref('');
const auth = ref(false), register = ref(false), phone = ref(''), password = ref(''), saving = ref(false), reviewOrder = ref(null), rating = ref(5), content = ref('');
let initial = null; try { initial = JSON.parse(sessionStorage.getItem('life-session')); } catch {}
const session = ref(initial);
let generation = 0;
async function api(path, options = {}) {
  try { return await request(path, {...options, token: session.value?.token}); }
  catch(e) { if(e.status === 401) { logout(); auth.value = true; } throw e; }
}
function logout() { session.value = null; sessionStorage.removeItem('life-session'); orders.value = {records:[],current:1,pages:0}; }
async function load(current = 1) {
  const run = ++generation; busy.value = true; error.value = '';
  try {
    if(page.value === 'discover') {
      const data = await api(`/shops?${new URLSearchParams({current,size:9,...(keyword.value ? {keyword:keyword.value.trim()} : {}),...(typeId.value ? {typeId:typeId.value} : {})})}`);
      if(run === generation) shops.value = data;
    } else if(page.value === 'orders' && session.value) {
      const data = await api(`/voucher_orders/my?current=${current}&size=10`);
      if(run === generation) orders.value = data;
    }
  } catch(e) { if(run === generation) error.value = e.message; }
  finally { if(run === generation) busy.value = false; }
}
function navigate(next) { page.value = next; shop.value = null; notice.value = ''; load(); }
async function openShop(item) {
  page.value = 'detail'; shop.value = item; error.value = ''; busy.value = true;
  vouchers.value = {records:[],current:1,pages:0}; reviews.value = {records:[],current:1,pages:0};
  const run = ++generation;
  try {
    const data = await Promise.all([api(`/shops/${item.id}`),api(`/vouchers/shop/${item.id}?size=10`),api(`/reviews/shop/${item.id}?size=10`)]);
    if(run === generation) [shop.value,vouchers.value,reviews.value] = data;
  } catch(e) { if(run === generation) error.value = e.message; }
  finally { if(run === generation) busy.value = false; }
}
async function more(kind) {
  saving.value = true;
  const target = kind === 'vouchers' ? vouchers : reviews;
  try { target.value = await api(`/${kind}/shop/${shop.value.id}?current=${target.value.current+1}&size=10`); }
  catch(e) { error.value = e.message; } finally { saving.value = false; }
}
async function login() {
  saving.value = true; error.value = '';
  try {
    if(register.value) await api('/users/register',{method:'POST',body:{phone:phone.value,password:password.value}});
    session.value = await api('/users/login',{method:'POST',body:{phone:phone.value,password:password.value}});
    sessionStorage.setItem('life-session',JSON.stringify(session.value)); password.value = ''; auth.value = false;
    if(page.value === 'orders') await load();
  } catch(e) { error.value = e.message; } finally { saving.value = false; }
}
async function claim(voucher) {
  if(!session.value) { auth.value = true; return; }
  saving.value = true; error.value = ''; notice.value = '';
  try { const id = await api(`/voucher_orders/seckill/${voucher.id}`,{method:'POST'}); notice.value = `申请已受理，订单号 ${id}。订单生成后可在「我的订单」查看。`; }
  catch(e) { error.value = e.message; } finally { saving.value = false; }
}
async function submitReview() {
  saving.value = true; error.value = '';
  try { await api('/reviews',{method:'POST',body:{orderId:reviewOrder.value.id,rating:rating.value,content:content.value}}); reviewOrder.value = null; content.value = ''; notice.value = '评价已发布，感谢分享。'; }
  catch(e) { error.value = e.message; } finally { saving.value = false; }
}
const date = value => value ? value.replace('T',' ').slice(0,16) : '暂无';
onMounted(() => load());
</script>

<template>
  <header><a class="brand" href="#" @click.prevent="navigate('discover')"><span class="brand-icon">街</span>街里<span class="brand-caption">生活，就在附近</span></a><nav><button :class="{active:page!=='orders'}" @click="navigate('discover')"><Compass :size="18"/>发现</button><button :class="{active:page==='orders'}" @click="navigate('orders')"><Ticket :size="18"/>我的订单</button></nav><button class="account" @click="auth=true"><UserRound :size="18"/>{{session ? session.phone.slice(0,3)+'****'+session.phone.slice(-4) : '登录 / 注册'}}</button><button v-if="session" class="icon" title="退出登录" @click="logout"><LogOut :size="18"/></button></header>
  <main>
    <div v-if="notice" class="notice" role="status">{{notice}}<button class="icon" aria-label="关闭提示" @click="notice='' "><X :size="16"/></button></div>
    <div v-if="error && !auth && !reviewOrder" class="error" role="alert">{{error}}<button @click="page==='detail'?openShop(shop):load()">重试</button></div>
    <template v-if="page==='discover'">
      <section class="intro"><div><p class="eyebrow">LOCAL LIFE · 街里</p><h1>给日常，留一点好时光。</h1><p>一顿好饭，一杯咖啡，和附近值得一去的小店。</p></div><img src="https://images.unsplash.com/photo-1442512595331-e89e73853f31?w=700&auto=format&fit=crop&q=85" alt="咖啡与日常"/></section>
      <form class="searchbar" @submit.prevent="load()"><Search :size="21"/><input v-model="keyword" placeholder="找一家想去的店" aria-label="店铺名称"/><label>分类 ID <input v-model="typeId" type="number" min="1" aria-label="分类 ID"/></label><button class="primary">搜索</button></form>
      <div class="section-heading"><h2>附近的小美好</h2><span>{{shops.total || 0}} 家店铺</span><button class="icon" title="刷新店铺" @click="load()"><RefreshCw :size="18"/></button></div>
      <div v-if="busy" class="empty">正在寻找附近的好店…</div>
      <div v-else-if="!shops.records.length" class="empty"><Compass :size="36"/><h3>{{error?'暂时没能加载店铺':'还没有找到店铺'}}</h3><p>{{error?'稍后再试试吧':'换个关键词，或等新店开张。'}}</p></div>
      <div v-else class="shop-grid"><button v-for="item in shops.records" :key="item.id" class="shop-card" @click="openShop(item)"><div class="shop-art"><MapPin :size="38"/><span>{{item.name.slice(0,1)}}</span></div><div class="shop-copy"><h3>{{item.name}}</h3><p><MapPin :size="14"/>{{item.address || '地址待补充'}}</p><div class="shop-bottom"><span>人均 <b>¥{{item.avgPrice ?? '—'}}</b></span><ArrowRight :size="19"/></div></div></button></div>
      <div v-if="shops.pages>1" class="pagination"><button :disabled="busy || shops.current<=1" @click="load(shops.current-1)"><ArrowLeft :size="18"/></button><span>{{shops.current}} / {{shops.pages}}</span><button :disabled="busy || shops.current>=shops.pages" @click="load(shops.current+1)"><ArrowRight :size="18"/></button></div>
    </template>
    <template v-else-if="page==='detail' && shop">
      <button class="back" @click="navigate('discover')"><ArrowLeft :size="18"/>返回发现</button><section class="shop-title"><p class="eyebrow">街里 · 好店</p><h1>{{shop.name}}</h1><p><MapPin :size="17"/>{{shop.address || '地址待补充'}}</p><span>人均 ¥{{shop.avgPrice ?? '—'}}</span></section>
      <div v-if="busy" class="empty">正在加载店铺…</div><template v-else><div class="section-heading"><h2>店里的好优惠</h2><span>限量秒杀</span></div><div class="voucher-grid"><article v-for="v in vouchers.records" :key="v.id" class="voucher"><div class="price">¥<strong>{{v.payValue}}</strong><small>面值 ¥{{v.discountAmount}}</small></div><div><h3>{{v.title}}</h3><p>{{v.subTitle}}</p><small>{{date(v.seckillBeginTime)}} 至 {{date(v.seckillEndTime)}}</small><p>场次库存 {{v.seckillStock}}</p><button class="primary" :disabled="saving" @click="claim(v)">立即领取</button></div></article></div><p v-if="!vouchers.records.length" class="empty">店铺暂时没有可领取的优惠券</p><button v-if="vouchers.current<vouchers.pages" :disabled="saving" @click="more('vouchers')">下一页优惠券</button>
      <div class="section-heading"><h2>到店的人怎么说</h2><span>{{reviews.total || 0}} 条评价</span></div><article v-for="r in reviews.records" :key="r.id" class="review"><div class="review-heading"><UserRound :size="20"/><b>用户 {{r.userId}}</b><span class="stars">{{'★'.repeat(r.rating)}}</span><small>{{date(r.createTime)}}</small></div><p>{{r.content}}</p></article><p v-if="!reviews.records.length" class="empty">还没有评价，期待第一份分享。</p><button v-if="reviews.current<reviews.pages" :disabled="saving" @click="more('reviews')">下一页评价</button></template>
    </template>
    <template v-else-if="page==='orders'"><div class="section-heading"><h1>我的订单</h1><button class="icon" title="刷新订单" @click="load()"><RefreshCw :size="20"/></button></div><div v-if="!session" class="empty"><Ticket :size="40"/><h3>把期待装进口袋</h3><p>登录后查看你的优惠券订单</p><button class="primary" @click="auth=true">登录</button></div><div v-else-if="busy" class="empty">正在加载订单…</div><div v-else-if="!orders.records.length" class="empty"><Ticket :size="40"/><h3>还没有订单</h3><p>刚提交的领取申请可能需要稍等片刻。</p><button @click="navigate('discover')">去逛逛</button></div><article v-for="o in orders.records" v-else :key="o.id" class="order"><Ticket :size="28"/><div><h3>优惠券 #{{o.voucherId}}</h3><p>订单 {{o.id}}</p><small>{{date(o.createTime)}}</small></div><span>{{o.orderStatus===1?'已领取':'状态 '+o.orderStatus}}</span><button @click="reviewOrder=o;error=''">写评价</button></article><div v-if="orders.pages>1" class="pagination"><button :disabled="orders.current<=1 || busy" @click="load(orders.current-1)"><ArrowLeft :size="18"/></button>{{orders.current}} / {{orders.pages}}<button :disabled="orders.current>=orders.pages || busy" @click="load(orders.current+1)"><ArrowRight :size="18"/></button></div></template>
  </main><footer><b>街里</b><span>平凡的日子，也值得好好过。</span><span>LOCAL LIFE / 2026</span></footer>
  <div v-if="auth || reviewOrder" class="overlay" @click.self="auth=false;reviewOrder=null"><section class="modal" role="dialog" aria-modal="true" :aria-label="auth?'用户登录':'发布评价'"><button class="icon close" aria-label="关闭" @click="auth=false;reviewOrder=null"><X/></button><form v-if="auth" @submit.prevent="login"><p class="eyebrow">欢迎来到街里</p><h2>{{register?'开启你的附近生活':'好久不见，欢迎回来'}}</h2><label>手机号<input v-model="phone" required pattern="1[3-9][0-9]{9}" autocomplete="tel" inputmode="tel" placeholder="请输入手机号"/></label><label>密码<input v-model="password" required type="password" minlength="6" maxlength="64" :autocomplete="register?'new-password':'current-password'" placeholder="6–64 位密码"/></label><p v-if="error" class="error" role="alert">{{error}}</p><button class="primary wide" :disabled="saving">{{saving?'请稍候…':register?'注册并登录':'登录'}}</button><button type="button" class="text-button" @click="register=!register;error=''">{{register?'已有账号？去登录':'还没有账号？注册'}}</button></form><form v-else @submit.prevent="submitReview"><h2>分享你的到店感受</h2><label>评分<select v-model.number="rating"><option v-for="n in 5" :value="n">{{n}} 分</option></select></label><label>评价<textarea v-model="content" required maxlength="1000" rows="5" placeholder="说说你的真实体验"/></label><p v-if="error" class="error">{{error}}</p><button class="primary wide" :disabled="saving">发布评价</button></form></section></div>
</template>
