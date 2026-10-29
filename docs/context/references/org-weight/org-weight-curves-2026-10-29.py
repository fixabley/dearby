# 관심 조직 가중치 곡선 비교(2026-10-29, native-v1.md "관심 조직 가중치" 근거). 이 폴더에서 python3 org-weight-curves-2026-10-29.py (numpy·matplotlib, macOS AppleGothic 글꼴)
import numpy as np, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
plt.rcParams["font.family"]="AppleGothic"; plt.rcParams["axes.unicode_minus"]=False
S=np.linspace(0,4,400)
fig,ax=plt.subplots(figsize=(8,5),dpi=160)
ax.plot(S,1-np.exp(-S),lw=3,color="#2563eb",label="1 - e^(-S)  (추천)")
ax.plot(S,np.minimum(1,S),lw=2,ls="--",color="#dc2626",label="min(1, S)")
ax.plot(S,np.minimum(1,S/2),lw=2,ls="--",color="#f59e0b",label="min(1, S÷2)")
ax.plot(S,np.log1p(S),lw=2,ls=":",color="#6b7280",label="log(1 + S)  (상한 없음)")
for s,t in [(0.4,"♥"),(1.1,"지원+♥"),(2.2,"+참여"),(4.0,"여러 기수")]:
    y=1-np.exp(-s); ax.scatter([s],[y],color="#2563eb",zorder=5)
    ax.annotate(f"{t}\n{y:.2f}",(s,y),textcoords="offset points",xytext=(0,-30),ha="center",fontsize=9,color="#1e3a8a")
ax.axhline(1,color="#9ca3af",lw=0.8)
ax.set_xlabel("조직별 신호 합 S"); ax.set_ylabel("관심 조직 가중치")
ax.set_xlim(0,4); ax.set_ylim(0,1.65); ax.grid(alpha=.25); ax.legend(loc="upper left",fontsize=9)
ax.set_title("반응이 쌓일수록: 지수식은 계속 오르되 1을 넘지 않음")
fig.tight_layout(); fig.savefig("org-weight-curves-2026-10-29.png")
