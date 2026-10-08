import { useOutletContext } from 'react-router';
import type { MeResponse } from '../../api/types';

export interface ManageContext {
  me: MeResponse;
  blogId: number;
  blogName: string;
}

export function useManage(): ManageContext {
  return useOutletContext<ManageContext>();
}
