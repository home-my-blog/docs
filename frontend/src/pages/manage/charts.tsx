import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatShortDay } from '../../lib/format';

const VIEWS = '#f25c2b';
const VISITORS = '#2b8af2';
const COMMENTS = '#22a06b';

interface ViewsPoint {
  date: string;
  views: number;
  visitors: number;
}

/** 일별 조회수·방문자 선 그래프 (BM-02-2, BM-06-2) */
export function ViewsChart({ data, height = 240 }: { data: ViewsPoint[]; height?: number }) {
  return (
    <div className="chart" style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="#e8e8e8" vertical={false} />
          <XAxis dataKey="date" tickFormatter={formatShortDay} tick={{ fontSize: 11, fill: '#888888' }} tickLine={false} axisLine={false} minTickGap={16} />
          <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#888888' }} tickLine={false} axisLine={false} width={44} />
          <Tooltip labelFormatter={(l) => String(l)} contentStyle={{ borderRadius: 8, borderColor: '#e8e8e8', fontSize: 12 }} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Line type="monotone" dataKey="views" name="조회수" stroke={VIEWS} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
          <Line type="monotone" dataKey="visitors" name="방문자" stroke={VISITORS} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

/** 일별 댓글 수 막대 그래프 (BM-06-2) */
export function CommentsChart({ data, height = 200 }: { data: { date: string; comments: number }[]; height?: number }) {
  return (
    <div className="chart" style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="#e8e8e8" vertical={false} />
          <XAxis dataKey="date" tickFormatter={formatShortDay} tick={{ fontSize: 11, fill: '#888888' }} tickLine={false} axisLine={false} minTickGap={16} />
          <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#888888' }} tickLine={false} axisLine={false} width={44} />
          <Tooltip contentStyle={{ borderRadius: 8, borderColor: '#e8e8e8', fontSize: 12 }} cursor={{ fill: '#f4f4f4' }} />
          <Bar dataKey="comments" name="댓글" fill={COMMENTS} radius={[3, 3, 0, 0]} maxBarSize={24} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
